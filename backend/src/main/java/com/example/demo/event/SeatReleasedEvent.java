package com.example.demo.event;

public record SeatReleasedEvent(
    Long eventId,
    Long seatId
) {}
