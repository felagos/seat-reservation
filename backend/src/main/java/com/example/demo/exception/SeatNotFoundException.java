package com.example.demo.exception;

/**
 * Seat does not exist. Thrown when a requested seat ID has no matching row, or a multi-seat
 * request's seat count doesn't match the number of rows found (some IDs don't exist).
 * HTTP 404 Not Found.
 */
public class SeatNotFoundException extends RuntimeException {
    private final Long seatId;

    public SeatNotFoundException(Long seatId) {
        super(seatId != null ? "Seat " + seatId + " not found" : "One or more seats not found");
        this.seatId = seatId;
    }

    public Long getSeatId() {
        return seatId;
    }
}
