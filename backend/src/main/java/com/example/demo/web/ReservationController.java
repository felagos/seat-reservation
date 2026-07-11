package com.example.demo.web;

import com.example.demo.service.SeatHoldService;
import com.example.demo.web.dto.ConfirmReservationRequest;
import com.example.demo.web.dto.ReservationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/reservations")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ReservationController {
    private final SeatHoldService seatHoldService;

    public ReservationController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    @PostMapping
    public ResponseEntity<?> confirmReservation(
        @PathVariable Long eventId,
        @RequestHeader("X-Client-Id") String clientId,
        @RequestBody ConfirmReservationRequest request
    ) {
        try {
            var reservation = seatHoldService.confirm(eventId, request.seatIds(), clientId);
            return ResponseEntity.ok(ReservationResponse.fromReservation(reservation));
        } catch (Exception e) {
            if (e.getMessage().contains("expired")) {
                return ResponseEntity.status(410).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
