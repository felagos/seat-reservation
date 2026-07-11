package com.example.demo.exception;

public class SeatNotOwnedException extends RuntimeException {
    private final Long seatId;

    public SeatNotOwnedException(Long seatId) {
        super("Seat " + seatId + " is not owned by this client");
        this.seatId = seatId;
    }

    public Long getSeatId() {
        return seatId;
    }
}
