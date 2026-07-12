package com.example.demo.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for reservation confirmation endpoint.
 * Contains list of held seat IDs to confirm atomically.
 */
public record ConfirmReservationRequest(
    @NotEmpty(message = "must not be empty")
    @Size(max = SeatRequestLimits.MAX_SEATS_PER_REQUEST, message = "must not exceed " + SeatRequestLimits.MAX_SEATS_PER_REQUEST + " seats")
    List<@NotNull Long> seatIds
) {}
