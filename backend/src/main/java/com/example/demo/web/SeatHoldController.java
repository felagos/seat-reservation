package com.example.demo.web;

import com.example.demo.service.SeatHoldService;
import com.example.demo.web.dto.ClientIdConstraints;
import com.example.demo.web.dto.HoldRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoint for seat hold and release operations.
 * All endpoints require X-Client-Id header for client identification.
 * Error mapping (unavailable, ownership, lock timeout) is handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/events/{eventId}/seats")
@Validated
public class SeatHoldController {
    private final SeatHoldService seatHoldService;

    public SeatHoldController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    /**
     * Hold a single seat for the client.
     * POST /api/events/{eventId}/seats/{seatId}/hold
     */
    @PostMapping("/{seatId}/hold")
    public ResponseEntity<SeatHoldService.SeatHoldResponse> holdSeat(
        @PathVariable Long eventId,
        @PathVariable Long seatId,
        @RequestHeader("X-Client-Id") @Pattern(regexp = ClientIdConstraints.UUID_REGEX, message = "must be a valid UUID") String clientId
    ) {
        return ResponseEntity.ok(seatHoldService.hold(eventId, seatId, clientId));
    }

    /**
     * Hold multiple seats for the client, atomically.
     * POST /api/events/{eventId}/seats/hold
     */
    @PostMapping("/hold")
    public ResponseEntity<List<SeatHoldService.SeatHoldResponse>> holdMultipleSeat(
        @PathVariable Long eventId,
        @RequestHeader("X-Client-Id") @Pattern(regexp = ClientIdConstraints.UUID_REGEX, message = "must be a valid UUID") String clientId,
        @Valid @RequestBody HoldRequest request
    ) {
        return ResponseEntity.ok(seatHoldService.hold(eventId, request.seatIds(), clientId));
    }

    /**
     * Release a held seat back to AVAILABLE.
     * DELETE /api/events/{eventId}/seats/{seatId}/hold
     */
    @DeleteMapping("/{seatId}/hold")
    public ResponseEntity<Void> releaseSeat(
        @PathVariable Long eventId,
        @PathVariable Long seatId,
        @RequestHeader("X-Client-Id") @Pattern(regexp = ClientIdConstraints.UUID_REGEX, message = "must be a valid UUID") String clientId
    ) {
        seatHoldService.release(seatId, clientId);
        return ResponseEntity.noContent().build();
    }
}
