package com.example.demo.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Seat entity. Represents a single seat in an event, with concurrency-safe status tracking.
 * Status: AVAILABLE (free) | HELD (temporarily reserved, expires after TTL) | RESERVED (confirmed).
 * When HELD: held_by (clientId) and held_until (expiration) are set.
 * Indexes: (event_id, status) for availability search, (held_until) for sweep job expiry.
 */
@Entity
@Table(name = "seats", indexes = {
    @Index(name = "idx_event_status", columnList = "event_id, status"),
    @Index(name = "idx_held_until", columnList = "held_until")
})
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String rowLabel;

    @Column(nullable = false)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(name = "held_by")
    private String heldBy;

    @Column(name = "held_until")
    private Instant heldUntil;

    @Column(name = "reservation_id")
    private Long reservationId;

    /**
     * No-arg constructor for JPA.
     */
    public Seat() {}

    /**
     * Constructor for new seat creation. Initializes with AVAILABLE status.
     *
     * @param event parent event
     * @param rowLabel seat row (e.g., "A", "B")
     * @param seatNumber seat number within row (e.g., "1", "2")
     */
    public Seat(Event event, String rowLabel, String seatNumber) {
        this.event = event;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    /** Get seat ID. */
    public Long getId() {
        return id;
    }

    /** Set seat ID. */
    public void setId(Long id) {
        this.id = id;
    }

    /** Get parent event. */
    public Event getEvent() {
        return event;
    }

    /** Set parent event. */
    public void setEvent(Event event) {
        this.event = event;
    }

    /** Get row label (e.g., "A", "B"). */
    public String getRowLabel() {
        return rowLabel;
    }

    /** Set row label. */
    public void setRowLabel(String rowLabel) {
        this.rowLabel = rowLabel;
    }

    /** Get seat number within row. */
    public String getSeatNumber() {
        return seatNumber;
    }

    /** Set seat number. */
    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    /** Get seat status (AVAILABLE, HELD, RESERVED). */
    public SeatStatus getStatus() {
        return status;
    }

    /** Set seat status. */
    public void setStatus(SeatStatus status) {
        this.status = status;
    }

    /** Get client ID that holds this seat (null if AVAILABLE or RESERVED). */
    public String getHeldBy() {
        return heldBy;
    }

    /** Set client ID holding this seat. */
    public void setHeldBy(String heldBy) {
        this.heldBy = heldBy;
    }

    /** Get hold expiration time (null if not HELD). */
    public Instant getHeldUntil() {
        return heldUntil;
    }

    /** Set hold expiration time. */
    public void setHeldUntil(Instant heldUntil) {
        this.heldUntil = heldUntil;
    }

    /** Get reservation ID if RESERVED (null otherwise). */
    public Long getReservationId() {
        return reservationId;
    }

    /** Set reservation ID. */
    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }
}
