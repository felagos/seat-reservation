package com.example.demo.exception;

public class SeatLockTimeoutException extends RuntimeException {
    private final Long seatId;

    public SeatLockTimeoutException(Long seatId) {
        super("Failed to acquire lock for seat " + seatId);
        this.seatId = seatId;
    }

    public Long getSeatId() {
        return seatId;
    }
}
