package com.example.demo.web;

import com.example.demo.service.SeatHoldService;
import com.example.demo.web.dto.HoldRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/seats")
@CrossOrigin(origins = "*", maxAge = 3600)
public class SeatHoldController {
    private final SeatHoldService seatHoldService;

    public SeatHoldController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

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
