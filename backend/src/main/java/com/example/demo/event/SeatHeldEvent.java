package com.example.demo.event;

import java.time.Instant;

/**
 * Domain event published when seat is held.
 * Processed by SeatEventListener AFTER_COMMIT to publish to Redis → SSE broadcast.
 */
public record SeatHeldEvent(
    Long eventId,
    Long seatId,
    String heldBy,
    Instant expiresAt
) {}
