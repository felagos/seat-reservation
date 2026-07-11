package com.example.demo.service;

import com.example.demo.domain.Seat;
import com.example.demo.domain.SeatStatus;
import com.example.demo.event.SeatReleasedEvent;
import com.example.demo.repository.SeatRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@EnableScheduling
public class SeatSweepService {
    private final SeatRepository seatRepository;
    private final SeatLockRegistry lockRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<SeatSweepService> selfProvider;

    @Value("${seat.lock.timeout-ms:3000}")
    private long lockTimeoutMs;

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

    @Scheduled(fixedDelayString = "${seat.sweep.interval-ms:15000}")
    public void sweepExpiredHolds() {
        List<Seat> expiredSeats = seatRepository.findByStatusAndHeldUntilBefore(
            SeatStatus.HELD,
            Instant.now()
        );

        for (Seat seat : expiredSeats) {
            try {
                lockRegistry.withLocks(List.of(seat.getId()), lockTimeoutMs, () -> {
                    selfProvider.getObject().doExpireTx(seat.getId());
                    return null;
                });
            } catch (Exception e) {
                // Log but continue with other seats
            }
        }
    }

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
            seatRepository.save(seat);
            eventPublisher.publishEvent(new SeatReleasedEvent(eventId, seatId));
        }
    }
}
