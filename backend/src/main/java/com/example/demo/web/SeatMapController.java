package com.example.demo.web;

import com.example.demo.domain.Seat;
import com.example.demo.repository.SeatRepository;
import com.example.demo.web.dto.SeatDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events/{eventId}/seats")
@CrossOrigin(origins = "*", maxAge = 3600)
public class SeatMapController {
    private final SeatRepository seatRepository;

    public SeatMapController(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

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
