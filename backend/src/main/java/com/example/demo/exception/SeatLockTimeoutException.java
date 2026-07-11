package com.example.demo.exception;

/**
 * Lock acquisition timeout error. In-memory lock not acquired within timeout.
 * HTTP 409 Conflict. Indicates high contention on seat.
 */
public class SeatLockTimeoutException extends RuntimeException {
    private final Long seatId;

    /**
     * Constructor with seat ID.
     *
     * @param seatId seat that lock timed out on
     */
    public SeatLockTimeoutException(Long seatId) {
        super("Failed to acquire lock for seat " + seatId);
        this.seatId = seatId;
    }

    /**
     * Get seat ID that timed out.
     *
     * @return seat ID
     */
    public Long getSeatId() {
        return seatId;
    }
}
