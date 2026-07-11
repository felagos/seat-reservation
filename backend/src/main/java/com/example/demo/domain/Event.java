package com.example.demo.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Event entity. Represents a ticketed event (concert, show, etc.).
 * Contains basic metadata. Seats belong to events via foreign key.
 */
@Entity
@Table(name = "events")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Instant startsAt;

    /**
     * No-arg constructor for JPA.
     */
    public Event() {}

    /**
     * Constructor for event creation.
     *
     * @param name event name/title
     * @param startsAt start timestamp
     */
    public Event(String name, Instant startsAt) {
        this.name = name;
        this.startsAt = startsAt;
    }

    /** Get event ID. */
    public Long getId() {
        return id;
    }

    /** Set event ID. */
    public void setId(Long id) {
        this.id = id;
    }

    /** Get event name/title. */
    public String getName() {
        return name;
    }

    /** Set event name/title. */
    public void setName(String name) {
        this.name = name;
    }

    /** Get start timestamp. */
    public Instant getStartsAt() {
        return startsAt;
    }

    /** Set start timestamp. */
    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }
}
