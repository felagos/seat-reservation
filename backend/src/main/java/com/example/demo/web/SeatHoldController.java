package com.example.demo.web;

import com.example.demo.service.SeatHoldService;
import com.example.demo.web.dto.HoldRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint for seat hold and release operations.
 * All endpoints require X-Client-Id header for client identification.
 * Hold timeout returns 409 Conflict. Release permission errors return 403 Forbidden.
 */
@RestController
@RequestMapping("/api/events/{eventId}/seats")
@CrossOrigin(origins = "*", maxAge = 3600)
public class SeatHoldController {
    private final SeatHoldService seatHoldService;

    /**
     * Constructor with service injection.
     *
     * @param seatHoldService the hold service
     */
    public SeatHoldController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    /**
     * Hold a single seat for the client.
     * POST /api/events/{eventId}/seats/{seatId}/hold
     *
     * @param eventId event ID
     * @param seatId seat ID to hold
     * @param clientId client from X-Client-Id header
     * @return 200 SeatHoldResponse with expiration, or 409 Conflict if unavailable/timeout
     */
    @PostMapping("/{seatId}/hold")
    public ResponseEntity<?> holdSeat(
        @PathVariable Long eventId,
        @PathVariable Long seatId,
        @RequestHeader("X-Client-Id") String clientId
    ) {
        try {
            var response = seatHoldService.hold(eventId, seatId, clientId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    /**
     * Hold multiple seats for the client.
     * POST /api/events/{eventId}/seats/hold
     *
     * @param eventId event ID
     * @param clientId client from X-Client-Id header
     * @param request HoldRequest with list of seat IDs
     * @return 200 list of SeatHoldResponse, or 409 Conflict if any unavailable/timeout
     */
    @PostMapping("/hold")
    public ResponseEntity<?> holdMultipleSeat(
        @PathVariable Long eventId,
        @RequestHeader("X-Client-Id") String clientId,
        @RequestBody HoldRequest request
    ) {
        try {
            var response = seatHoldService.hold(eventId, request.seatIds(), clientId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    /**
     * Release a held seat back to AVAILABLE.
     * DELETE /api/events/{eventId}/seats/{seatId}/hold
     *
     * @param eventId event ID
     * @param seatId seat ID to release
     * @param clientId client from X-Client-Id header
     * @return 204 No Content on success, or 403 Forbidden if not held by this client
     */
    @DeleteMapping("/{seatId}/hold")
    public ResponseEntity<?> releaseSeat(
        @PathVariable Long eventId,
        @PathVariable Long seatId,
        @RequestHeader("X-Client-Id") String clientId
    ) {
        try {
            seatHoldService.release(seatId, clientId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }
}
