package com.example.demo.service;

import com.example.demo.domain.Reservation;
import com.example.demo.domain.Seat;
import com.example.demo.domain.SeatStatus;
import com.example.demo.exception.HoldExpiredException;
import com.example.demo.exception.SeatHoldLimitExceededException;
import com.example.demo.exception.SeatNotFoundException;
import com.example.demo.exception.SeatNotOwnedException;
import com.example.demo.exception.SeatUnavailableException;
import com.example.demo.event.SeatHeldEvent;
import com.example.demo.event.SeatReleasedEvent;
import com.example.demo.event.SeatReservedEvent;
import com.example.demo.repository.ReservationRepository;
import com.example.demo.repository.SeatRepository;
import com.example.demo.web.dto.SeatRequestLimits;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for seat hold, release, and confirmation operations.
 * Coordinates pessimistic locking (in-memory + DB) with transactional event publishing.
 * All multi-seat operations are atomic: acquire locks sequentially (sorted), execute single transaction.
 */
@Service
public class SeatHoldService {
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final SeatLockRegistry lockRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<SeatHoldService> selfProvider;

    @Value("${seat.hold.ttl-seconds:120}")
    private long holdTtlSeconds;

    @Value("${seat.lock.timeout-ms:3000}")
    private long lockTimeoutMs;

    public SeatHoldService(
        SeatRepository seatRepository,
        ReservationRepository reservationRepository,
        SeatLockRegistry lockRegistry,
        ApplicationEventPublisher eventPublisher,
        ObjectProvider<SeatHoldService> selfProvider
    ) {
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.lockRegistry = lockRegistry;
        this.eventPublisher = eventPublisher;
        this.selfProvider = selfProvider;
    }

    /**
     * Hold a single seat for the given client.
     * Delegates to multi-seat hold and extracts result.
     *
     * @param seatId seat ID to hold
     * @param clientId client identifier (X-Client-Id header)
     * @return seat hold response with expiration
     * @throws SeatUnavailableException if seat unavailable
     * @throws SeatLockTimeoutException if lock acquisition times out
     */
    public SeatHoldResponse hold(Long seatId, String clientId) {
        return hold(List.of(seatId), clientId).get(0);
    }

    /**
     * Hold multiple seats for the given client.
     * Acquires in-memory locks (sorted by ID, deadlock-free), then executes doHoldTx atomically.
     *
     * @param seatIds list of seat IDs to hold (order irrelevant, sorted internally)
     * @param clientId client identifier (X-Client-Id header)
     * @return list of hold responses with expiration times
     * @throws SeatUnavailableException if any seat unavailable/expired
     * @throws SeatLockTimeoutException if lock acquisition times out
     */
    public List<SeatHoldResponse> hold(List<Long> seatIds, String clientId) {
        List<Long> sorted = seatIds.stream().distinct().sorted().collect(Collectors.toList());
        return lockRegistry.withLocks(sorted, lockTimeoutMs, () ->
            selfProvider.getObject().doHoldTx(sorted, clientId)
        );
    }

    /**
     * Transactional hold operation. Acquires pessimistic locks, verifies seat status, marks as HELD.
     * Double-check: re-verifies available status and lazy-expiry after lock acquired.
     * Publishes SeatHeldEvent after commit for SSE fanout via Redis.
     *
     * @param seatIds sorted list of seat IDs (pre-sorted by caller)
     * @param clientId client that holds seats
     * @return list of hold responses with expiration
     * @throws SeatUnavailableException if seat count mismatch or status invalid
     */
    @Transactional
    public List<SeatHoldResponse> doHoldTx(List<Long> seatIds, String clientId) {
        List<Seat> seats = seatRepository.findAllByIdForUpdate(seatIds);

        if (seats.size() != seatIds.size()) {
            throw new SeatNotFoundException(null);
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(holdTtlSeconds);
        int newGrants = 0;

        for (Seat seat : seats) {
            boolean availableOrExpired = seat.getStatus() == SeatStatus.AVAILABLE
                || (seat.getStatus() == SeatStatus.HELD && seat.getHeldUntil().isBefore(now));
            boolean ownRenewal = seat.getStatus() == SeatStatus.HELD && clientId.equals(seat.getHeldBy());

            if (!availableOrExpired && !ownRenewal) {
                throw new SeatUnavailableException(seat.getId());
            }
            if (!ownRenewal) {
                newGrants++;
            }
        }

        if (newGrants > 0) {
            long alreadyHeld = seatRepository.countByHeldByAndStatus(clientId, SeatStatus.HELD);
            if (alreadyHeld + newGrants > SeatRequestLimits.MAX_SEATS_PER_REQUEST) {
                throw new SeatHoldLimitExceededException(SeatRequestLimits.MAX_SEATS_PER_REQUEST);
            }
        }

        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.HELD);
            seat.setHeldBy(clientId);
            seat.setHeldUntil(expiresAt);
            eventPublisher.publishEvent(new SeatHeldEvent(seat.getId(), clientId, expiresAt));
        }

        return seats.stream()
            .map(s -> new SeatHoldResponse(s.getId(), s.getRowLabel(), s.getSeatNumber(), expiresAt))
            .collect(Collectors.toList());
    }

    /**
     * Release a held seat back to AVAILABLE, removing hold metadata.
     * Acquires lock, executes doReleaseTx atomically.
     *
     * @param seatId seat ID to release
     * @param clientId client that owns the hold (must match seat.held_by)
     * @throws SeatNotOwnedException if seat not held by this client
     * @throws SeatUnavailableException if seat not found
     * @throws SeatLockTimeoutException if lock acquisition times out
     */
    public void release(Long seatId, String clientId) {
        lockRegistry.withLocks(List.of(seatId), lockTimeoutMs, () -> {
            selfProvider.getObject().doReleaseTx(seatId, clientId);
            return null;
        });
    }

    /**
     * Transactional release operation. Verifies seat is HELD by clientId, reverts to AVAILABLE.
     * Publishes SeatReleasedEvent for SSE broadcast.
     *
     * @param seatId seat ID to release
     * @param clientId client identifier
     * @throws SeatNotOwnedException if seat not held by this client
     * @throws SeatUnavailableException if seat not found
     */
    @Transactional
    public void doReleaseTx(Long seatId, String clientId) {
        Seat seat = seatRepository.findByIdForUpdate(seatId)
            .orElseThrow(() -> new SeatUnavailableException(seatId));

        if (seat.getStatus() != SeatStatus.HELD || !clientId.equals(seat.getHeldBy())) {
            throw new SeatNotOwnedException(seatId);
        }

        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setHeldBy(null);
        seat.setHeldUntil(null);
        eventPublisher.publishEvent(new SeatReleasedEvent(seatId));
    }

    /**
     * Confirm multiple held seats into a reservation.
     * Acquires locks (sorted), executes doConfirmTx to transition seats to RESERVED and create Reservation.
     *
     * @param seatIds list of held seat IDs to reserve (order irrelevant, sorted internally)
     * @param clientId client that holds and reserves seats
     * @return created Reservation entity
     * @throws SeatNotOwnedException if any seat not held by this client
     * @throws SeatUnavailableException if any seat not in HELD status
     * @throws HoldExpiredException if any seat hold has expired
     * @throws SeatLockTimeoutException if lock acquisition times out
     */
    public Reservation confirm(List<Long> seatIds, String clientId) {
        List<Long> sorted = seatIds.stream().distinct().sorted().collect(Collectors.toList());
        return lockRegistry.withLocks(sorted, lockTimeoutMs, () ->
            selfProvider.getObject().doConfirmTx(sorted, clientId)
        );
    }

    /**
     * Transactional confirmation: creates Reservation, transitions seats HELD→RESERVED, publishes events.
     * Double-check: verifies each seat HELD, owned by clientId, not expired before transition.
     *
     * @param seatIds sorted list of seat IDs
     * @param clientId client identifier
     * @return saved Reservation with clientId
     * @throws SeatNotOwnedException if ownership mismatch
     * @throws SeatUnavailableException if seat not HELD
     * @throws HoldExpiredException if hold expired
     */
    @Transactional
    public Reservation doConfirmTx(List<Long> seatIds, String clientId) {
        List<Seat> seats = seatRepository.findAllByIdForUpdate(seatIds);

        if (seats.size() != seatIds.size()) {
            throw new SeatNotFoundException(null);
        }

        Instant now = Instant.now();
        for (Seat seat : seats) {
            if (seat.getStatus() != SeatStatus.HELD) {
                throw new SeatUnavailableException(seat.getId());
            }
            if (!clientId.equals(seat.getHeldBy())) {
                throw new SeatNotOwnedException(seat.getId());
            }
            if (seat.getHeldUntil().isBefore(now)) {
                throw new HoldExpiredException(seat.getId());
            }
        }

        var reservation = new Reservation(clientId);
        var saved = reservationRepository.save(reservation);

        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.RESERVED);
            seat.setReservationId(saved.getId());
            eventPublisher.publishEvent(new SeatReservedEvent(seat.getId(), saved.getId()));
        }

        return saved;
    }

    /**
     * Response for seat hold operation. Contains seat identity and hold expiration.
     */
    public record SeatHoldResponse(Long seatId, String rowLabel, String seatNumber, Instant expiresAt) {}
}
