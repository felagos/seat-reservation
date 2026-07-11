package com.example.demo.exception;

public class HoldExpiredException extends RuntimeException {
    private final Long seatId;

    public HoldExpiredException(Long seatId) {
        super("Hold for seat " + seatId + " has expired");
        this.seatId = seatId;
    }

    public Long getSeatId() {
        return seatId;
    }
}
