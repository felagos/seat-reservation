package com.example.demo.exception;

/**
 * Hold expiration error. Client attempting to confirm seat with expired hold.
 * HTTP 410 Gone.
 */
public class HoldExpiredException extends RuntimeException {
    private final Long seatId;

    /**
     * Constructor with seat ID.
     *
     * @param seatId seat whose hold expired
     */
    public HoldExpiredException(Long seatId) {
        super("Hold for seat " + seatId + " has expired");
        this.seatId = seatId;
    }

    /**
     * Get seat ID with expired hold.
     *
     * @return seat ID
     */
    public Long getSeatId() {
        return seatId;
    }
}
