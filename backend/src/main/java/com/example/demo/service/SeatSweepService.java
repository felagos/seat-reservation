package com.example.demo.service;

import com.example.demo.domain.Seat;
import com.example.demo.domain.SeatStatus;
import com.example.demo.event.SeatReleasedEvent;
import com.example.demo.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job for lazy expiration of holds.
 * Runs every 15s (configurable) to find expired HELD seats and revert to AVAILABLE.
 * Complements lazy-expiry logic in doHoldTx (double-check after lock).
 * Acquires locks, double-checks before reverting to handle concurrent operations.
 */
@Service
public class SeatSweepService {
    private static final Logger log = LoggerFactory.getLogger(SeatSweepService.class);
    private static final int SWEEP_BATCH_SIZE = 200;

    private final SeatRepository seatRepository;
    private final SeatLockRegistry lockRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<SeatSweepService> selfProvider;

    @Value("${seat.lock.timeout-ms:3000}")
    private long lockTimeoutMs;

    /**
     * Constructor with dependency injection.
     *
     * @param seatRepository seat data access
     * @param lockRegistry in-memory lock registry
     * @param eventPublisher application event publisher
     * @param selfProvider for proxy self-injection
     */
    public SeatSweepService(
        SeatRepository seatRepository,
        SeatLockRegistry lockRegistry,
        ApplicationEventPublisher eventPublisher,
        ObjectProvider<SeatSweepService> selfProvider
    ) {
        this.seatRepository = seatRepository;
        this.lockRegistry = lockRegistry;
        this.eventPublisher = eventPublisher;
        this.selfProvider = selfProvider;
    }

    /**
     * Sweep expired holds and revert to AVAILABLE, up to SWEEP_BATCH_SIZE per tick.
     * Scheduled at fixed interval (seat.sweep.interval-ms). Continues on individual failures;
     * any seats beyond the batch size are picked up on the next tick.
     */
    @Scheduled(fixedDelayString = "${seat.sweep.interval-ms:15000}")
    public void sweepExpiredHolds() {
        List<Seat> expiredSeats = seatRepository.findByStatusAndHeldUntilBefore(
            SeatStatus.HELD,
            Instant.now(),
            PageRequest.of(0, SWEEP_BATCH_SIZE)
        );

        for (Seat seat : expiredSeats) {
            try {
                lockRegistry.withLocks(List.of(seat.getId()), lockTimeoutMs, () -> {
                    selfProvider.getObject().doExpireTx(seat.getId());
                    return null;
                });
            } catch (Exception e) {
                log.warn("Failed to sweep expired hold for seat {}: {}", seat.getId(), e.getMessage());
            }
        }
    }

    /**
     * Transactional expiration: double-checks seat still expired under lock, then reverts to AVAILABLE.
     * Publishes SeatReleasedEvent for SSE broadcast.
     * Called from sweepExpiredHolds after lock acquired.
     *
     * @param seatId seat ID to expire
     */
    @Transactional
    public void doExpireTx(Long seatId) {
        Seat seat = seatRepository.findByIdForUpdate(seatId)
            .orElse(null);

        if (seat != null && seat.getStatus() == SeatStatus.HELD &&
            seat.getHeldUntil() != null && seat.getHeldUntil().isBefore(Instant.now())) {
            Long eventId = seat.getEvent().getId();
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHeldBy(null);
            seat.setHeldUntil(null);
            eventPublisher.publishEvent(new SeatReleasedEvent(eventId, seatId));
        }
    }
}
