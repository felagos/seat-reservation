package com.example.demo.web;

import com.example.demo.service.SeatQueryService;
import com.example.demo.web.dto.SeatDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoint for seat map retrieval.
 * Returns all seats for an event with status and ownership info.
 * Optional X-Client-Id sets heldByMe flag in seat DTOs.
 */
@RestController
@RequestMapping("/api/events/{eventId}/seats")
public class SeatMapController {
    private final SeatQueryService seatQueryService;

    public SeatMapController(SeatQueryService seatQueryService) {
        this.seatQueryService = seatQueryService;
    }

    /**
     * Get all seats for an event, sorted by row then seat number.
     * GET /api/events/{eventId}/seats
     */
    @GetMapping
    public List<SeatDto> getSeatMap(
        @PathVariable Long eventId,
        @RequestHeader(value = "X-Client-Id", required = false) String clientId
    ) {
        return seatQueryService.getSeatMap(eventId, clientId);
    }
}
