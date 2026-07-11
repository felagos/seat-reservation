package com.example.demo.exception;

/**
 * Seat availability error. Thrown when seat is not AVAILABLE or count mismatch on multi-seat hold.
 * HTTP 409 Conflict.
 */
public class SeatUnavailableException extends RuntimeException {
    private final Long seatId;

    /**
     * Constructor with seat ID.
     *
     * @param seatId seat that failed availability check
     */
    public SeatUnavailableException(Long seatId) {
        super("Seat " + seatId + " is not available");
        this.seatId = seatId;
    }

    /**
     * Get seat ID that failed.
     *
     * @return seat ID
     */
    public Long getSeatId() {
        return seatId;
    }
}
