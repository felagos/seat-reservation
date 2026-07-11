package com.example.demo.domain;

import jakarta.persistence.*;
import java.time.Instant;

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

    public Seat() {}

    public Seat(Event event, String rowLabel, String seatNumber) {
        this.event = event;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public void setRowLabel(String rowLabel) {
        this.rowLabel = rowLabel;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void setStatus(SeatStatus status) {
        this.status = status;
    }

    public String getHeldBy() {
        return heldBy;
    }

    public void setHeldBy(String heldBy) {
        this.heldBy = heldBy;
    }

    public Instant getHeldUntil() {
        return heldUntil;
    }

    public void setHeldUntil(Instant heldUntil) {
        this.heldUntil = heldUntil;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }
}
