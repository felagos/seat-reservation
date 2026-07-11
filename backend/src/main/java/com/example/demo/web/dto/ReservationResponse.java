package com.example.demo.web.dto;

import com.example.demo.domain.Reservation;

/**
 * Data Transfer Object for reservation response. Maps domain Reservation to API response.
 */
public record ReservationResponse(
    Long id,
    String holderId,
    String status
) {
    /**
     * Convert domain Reservation to ReservationResponse.
     *
     * @param reservation domain entity
     * @return populated response DTO
     */
    public static ReservationResponse fromReservation(Reservation reservation) {
        return new ReservationResponse(
            reservation.getId(),
            reservation.getHolderId(),
            reservation.getStatus().toString()
        );
    }
}
