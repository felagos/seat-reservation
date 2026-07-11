package com.example.demo.web;

import com.example.demo.service.SeatHoldService;
import com.example.demo.web.dto.ConfirmReservationRequest;
import com.example.demo.web.dto.ReservationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint for seat reservation confirmation.
 * Confirms held seats into a permanent reservation.
 * Requires X-Client-Id header. Returns 410 Gone if hold expired, 409 Conflict if seat unavailable.
 */
@RestController
@RequestMapping("/api/events/{eventId}/reservations")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ReservationController {
    private final SeatHoldService seatHoldService;

    /**
     * Constructor with service injection.
     *
     * @param seatHoldService the hold/confirmation service
     */
    public ReservationController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    /**
     * Confirm held seats into a reservation.
     * POST /api/events/{eventId}/reservations
     *
     * @param eventId event ID
     * @param clientId client from X-Client-Id header
     * @param request ConfirmReservationRequest with seat IDs to reserve
     * @return 200 ReservationResponse, 410 if hold expired, 409 if seat unavailable
     */
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
