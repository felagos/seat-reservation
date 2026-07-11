package com.example.demo.web.dto;

import java.util.List;

/**
 * Request body for reservation confirmation endpoint.
 * Contains list of held seat IDs to confirm atomically.
 */
public record ConfirmReservationRequest(List<Long> seatIds) {}
