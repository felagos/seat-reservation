package com.example.demo.web.dto;

import com.example.demo.domain.Reservation;

public record ReservationResponse(
    Long id,
    String holderId,
    String status
) {
    public static ReservationResponse fromReservation(Reservation reservation) {
        return new ReservationResponse(
            reservation.getId(),
            reservation.getHolderId(),
            reservation.getStatus().toString()
        );
    }
}
