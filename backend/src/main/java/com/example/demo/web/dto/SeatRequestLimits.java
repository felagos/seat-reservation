package com.example.demo.web.dto;

/**
 * Shared request-size limit for multi-seat operations (hold, confirm).
 * Matches the "up to 8 seats" limit advertised in the UI.
 */
public final class SeatRequestLimits {
    public static final int MAX_SEATS_PER_REQUEST = 8;

    private SeatRequestLimits() {}
}
