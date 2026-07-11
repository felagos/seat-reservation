package com.example.demo.event;

/**
 * Domain event published when seat is released or hold expires.
 * Processed by SeatEventListener AFTER_COMMIT to publish to Redis → SSE broadcast.
 */
public record SeatReleasedEvent(
    Long eventId,
    Long seatId
) {}
