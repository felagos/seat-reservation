package com.example.demo.event;

public record SeatReservedEvent(
    Long eventId,
    Long seatId,
    Long reservationId
) {}
