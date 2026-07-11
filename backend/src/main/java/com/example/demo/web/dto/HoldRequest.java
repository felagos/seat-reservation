package com.example.demo.web.dto;

import java.util.List;

/**
 * Request body for multi-seat hold endpoint.
 * Contains list of seat IDs to hold atomically.
 */
public record HoldRequest(List<Long> seatIds) {}
