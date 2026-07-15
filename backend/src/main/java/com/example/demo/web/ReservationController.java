package com.example.demo.web;

import com.example.demo.service.SeatHoldService;
import com.example.demo.web.dto.ClientIdConstraints;
import com.example.demo.web.dto.ConfirmReservationRequest;
import com.example.demo.web.dto.ReservationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint for seat reservation confirmation.
 * Confirms held seats into a permanent reservation.
 * Error mapping (expired hold, unavailable seat) is handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/reservations")
@Validated
public class ReservationController {
    private final SeatHoldService seatHoldService;

    public ReservationController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    /**
     * Confirm held seats into a reservation.
     * POST /api/reservations
     */
    @PostMapping
    public ResponseEntity<ReservationResponse> confirmReservation(
        @RequestHeader("X-Client-Id") @Pattern(regexp = ClientIdConstraints.UUID_REGEX, message = "must be a valid UUID") String clientId,
        @Valid @RequestBody ConfirmReservationRequest request
    ) {
        var reservation = seatHoldService.confirm(request.seatIds(), clientId);
        return ResponseEntity.ok(ReservationResponse.fromReservation(reservation));
    }
}
