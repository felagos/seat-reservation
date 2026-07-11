package com.example.demo.web;

import com.example.demo.domain.Seat;
import com.example.demo.repository.SeatRepository;
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
@CrossOrigin(origins = "*", maxAge = 3600)
public class SeatMapController {
    private final SeatRepository seatRepository;

    /**
     * Constructor with repository injection.
     *
     * @param seatRepository the seat data access repository
     */
    public SeatMapController(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    /**
     * Get all seats for an event, sorted by row then seat number.
     * GET /api/events/{eventId}/seats
     *
     * @param eventId event ID
     * @param clientId optional client from X-Client-Id header (sets heldByMe)
     * @return 200 list of SeatDto with status and ownership
     */
    @GetMapping
    public List<SeatDto> getSeatMap(
        @PathVariable Long eventId,
        @RequestHeader(value = "X-Client-Id", required = false) String clientId
    ) {
        return seatRepository.findByEventIdOrderByRowLabelAscSeatNumberAsc(eventId).stream()
            .map(seat -> SeatDto.fromSeat(seat, clientId != null ? clientId : ""))
            .toList();
    }
}
