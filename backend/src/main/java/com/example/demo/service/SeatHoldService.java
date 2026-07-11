package com.example.demo.service;

import com.example.demo.domain.Reservation;
import com.example.demo.domain.ReservationStatus;
import com.example.demo.domain.Seat;
import com.example.demo.domain.SeatStatus;
import com.example.demo.exception.HoldExpiredException;
import com.example.demo.exception.SeatNotOwnedException;
import com.example.demo.exception.SeatUnavailableException;
import com.example.demo.event.SeatHeldEvent;
import com.example.demo.event.SeatReleasedEvent;
import com.example.demo.event.SeatReservedEvent;
import com.example.demo.repository.ReservationRepository;
import com.example.demo.repository.SeatRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

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

    public SeatHoldResponse hold(Long eventId, Long seatId, String clientId) {
        return hold(eventId, List.of(seatId), clientId).get(0);
    }

    public List<SeatHoldResponse> hold(Long eventId, List<Long> seatIds, String clientId) {
        List<Long> sorted = seatIds.stream().sorted().collect(Collectors.toList());
        return lockRegistry.withLocks(sorted, lockTimeoutMs, () ->
            selfProvider.getObject().doHoldTx(eventId, sorted, clientId)
        );
    }

    @Transactional
    public List<SeatHoldResponse> doHoldTx(Long eventId, List<Long> seatIds, String clientId) {
        List<Seat> seats = seatRepository.findAllByIdForUpdate(seatIds);

        if (seats.size() != seatIds.size()) {
            throw new SeatUnavailableException(0L);
        }

        Instant expiresAt = Instant.now().plusSeconds(holdTtlSeconds);

        for (Seat seat : seats) {
            if (!seat.getEvent().getId().equals(eventId)) {
                throw new SeatUnavailableException(seat.getId());
            }

            if (seat.getStatus() == SeatStatus.AVAILABLE) {
                seat.setStatus(SeatStatus.HELD);
                seat.setHeldBy(clientId);
                seat.setHeldUntil(expiresAt);
                seatRepository.save(seat);
                eventPublisher.publishEvent(new SeatHeldEvent(eventId, seat.getId(), clientId, expiresAt));
            } else if (seat.getStatus() == SeatStatus.HELD && seat.getHeldUntil().isBefore(Instant.now())) {
                seat.setStatus(SeatStatus.HELD);
                seat.setHeldBy(clientId);
                seat.setHeldUntil(expiresAt);
                seatRepository.save(seat);
                eventPublisher.publishEvent(new SeatHeldEvent(eventId, seat.getId(), clientId, expiresAt));
            } else {
                throw new SeatUnavailableException(seat.getId());
            }
        }

        return seats.stream()
            .map(s -> new SeatHoldResponse(s.getId(), s.getRowLabel(), s.getSeatNumber(), expiresAt))
            .collect(Collectors.toList());
    }

    public void release(Long seatId, String clientId) {
        lockRegistry.withLocks(List.of(seatId), lockTimeoutMs, () -> {
            selfProvider.getObject().doReleaseTx(seatId, clientId);
            return null;
        });
    }

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
        seatRepository.save(seat);
        eventPublisher.publishEvent(new SeatReleasedEvent(seat.getEvent().getId(), seatId));
    }

    public Reservation confirm(Long eventId, List<Long> seatIds, String clientId) {
        List<Long> sorted = seatIds.stream().sorted().collect(Collectors.toList());
        return lockRegistry.withLocks(sorted, lockTimeoutMs, () ->
            selfProvider.getObject().doConfirmTx(eventId, sorted, clientId)
        );
    }

    @Transactional
    public Reservation doConfirmTx(Long eventId, List<Long> seatIds, String clientId) {
        List<Seat> seats = seatRepository.findAllByIdForUpdate(seatIds);

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

        var reservation = new Reservation(seats.get(0).getEvent(), clientId);
        var saved = reservationRepository.save(reservation);

        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.RESERVED);
            seat.setReservationId(saved.getId());
            seatRepository.save(seat);
            eventPublisher.publishEvent(new SeatReservedEvent(eventId, seat.getId(), saved.getId()));
        }

        return saved;
    }

    public record SeatHoldResponse(Long seatId, String rowLabel, String seatNumber, Instant expiresAt) {}
}
