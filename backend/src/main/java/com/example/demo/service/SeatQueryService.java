package com.example.demo.service;

import com.example.demo.repository.SeatRepository;
import com.example.demo.web.dto.SeatDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-only seat map queries. Kept separate from SeatHoldService (write path) so the
 * hot mutation path isn't coupled to a plain repository read.
 */
@Service
public class SeatQueryService {
    private final SeatRepository seatRepository;

    public SeatQueryService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    /**
     * Get all seats, sorted by row then seat number, with ownership resolved.
     *
     * @param clientId client to compare against held_by (empty string if not provided)
     * @return seat DTOs with heldByMe resolved
     */
    public List<SeatDto> getSeatMap(String clientId) {
        return seatRepository.findAllByOrderByRowLabelAscSeatNumberAsc().stream()
            .map(seat -> SeatDto.fromSeat(seat, clientId != null ? clientId : ""))
            .toList();
    }
}
