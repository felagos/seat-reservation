package com.example.demo.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Reservation entity. Represents confirmed seat reservation for a client.
 * Created during confirmation step (hold→reserve). One-to-many with seats via reservation_id.
 * Status currently always CONFIRMED (enum allows future states like CANCELLED).
 */
@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "holder_id", nullable = false)
    private String holderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * No-arg constructor for JPA.
     */
    public Reservation() {}

    /**
     * Constructor for reservation creation. Status defaults to CONFIRMED.
     *
     * @param event event being reserved
     * @param holderId client that holds the seats
     */
    public Reservation(Event event, String holderId) {
        this.event = event;
        this.holderId = holderId;
    }

    /** Get reservation ID. */
    public Long getId() {
        return id;
    }

    /** Set reservation ID. */
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

    /** Get holder client ID. */
    public String getHolderId() {
        return holderId;
    }

    /** Set holder client ID. */
    public void setHolderId(String holderId) {
        this.holderId = holderId;
    }

    /** Get reservation status. */
    public ReservationStatus getStatus() {
        return status;
    }

    /** Set reservation status. */
    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    /** Get creation timestamp. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Set creation timestamp. */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
