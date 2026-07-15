package com.example.demo.web;

import com.example.demo.service.SeatQueryService;
import com.example.demo.web.dto.SeatDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoint for seat map retrieval.
 * Returns all seats with status and ownership info.
 * Optional X-Client-Id sets heldByMe flag in seat DTOs.
 */
@RestController
@RequestMapping("/api/seats")
public class SeatMapController {
    private final SeatQueryService seatQueryService;

    public SeatMapController(SeatQueryService seatQueryService) {
        this.seatQueryService = seatQueryService;
    }

    /**
     * Get all seats, sorted by row then seat number.
     * GET /api/seats
     */
    @GetMapping
    public List<SeatDto> getSeatMap(
        @RequestHeader(value = "X-Client-Id", required = false) String clientId
    ) {
        return seatQueryService.getSeatMap(clientId);
    }
}
