package com.example.demo.web.dto;

import com.example.demo.domain.Seat;
import java.time.Instant;

public record SeatDto(
    Long id,
    String rowLabel,
    String seatNumber,
    String status,
    Boolean heldByMe,
    Instant expiresAt
) {
    public static SeatDto fromSeat(Seat seat, String clientId) {
        boolean heldByMe = "HELD".equals(seat.getStatus().toString()) && clientId.equals(seat.getHeldBy());
        return new SeatDto(
            seat.getId(),
            seat.getRowLabel(),
            seat.getSeatNumber(),
            seat.getStatus().toString(),
            heldByMe,
            heldByMe ? seat.getHeldUntil() : null
        );
    }
}
