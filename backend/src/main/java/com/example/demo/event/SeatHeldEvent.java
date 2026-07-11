package com.example.demo.event;

import java.time.Instant;

public record SeatHeldEvent(
    Long eventId,
    Long seatId,
    String heldBy,
    Instant expiresAt
) {}
