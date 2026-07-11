package com.example.demo.exception;

/**
 * Seat ownership error. Client attempting to release/confirm seat they don't hold.
 * HTTP 403 Forbidden.
 */
public class SeatNotOwnedException extends RuntimeException {
    private final Long seatId;

    /**
     * Constructor with seat ID.
     *
     * @param seatId seat not held by client
     */
    public SeatNotOwnedException(Long seatId) {
        super("Seat " + seatId + " is not owned by this client");
        this.seatId = seatId;
    }

    /**
     * Get seat ID not owned.
     *
     * @return seat ID
     */
    public Long getSeatId() {
        return seatId;
    }
}
