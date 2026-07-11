package com.example.demo.exception;

public class SeatUnavailableException extends RuntimeException {
    private final Long seatId;

    public SeatUnavailableException(Long seatId) {
        super("Seat " + seatId + " is not available");
        this.seatId = seatId;
    }

    public Long getSeatId() {
        return seatId;
    }
}
