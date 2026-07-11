package com.example.demo.web.dto;

import com.example.demo.domain.Seat;
import java.time.Instant;

/**
 * Data Transfer Object for seat view. Includes ownership flag for client-side UI rendering.
 * heldByMe true if seat status is HELD and held_by equals clientId.
 * expiresAt only populated if heldByMe is true.
 */
public record SeatDto(
    Long id,
    String rowLabel,
    String seatNumber,
    String status,
    Boolean heldByMe,
    Instant expiresAt
) {
    /**
     * Convert domain Seat to SeatDto with ownership comparison.
     *
     * @param seat domain entity
     * @param clientId client to compare against seat.held_by (determines heldByMe)
     * @return populated SeatDto
     */
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
