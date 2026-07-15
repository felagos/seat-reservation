package com.example.demo.event;

/**
 * Domain event published when seat is confirmed into reservation.
 * Processed by SeatEventListener AFTER_COMMIT to publish to Redis → SSE broadcast.
 */
public record SeatReservedEvent(
    Long seatId,
    Long reservationId
) {}
