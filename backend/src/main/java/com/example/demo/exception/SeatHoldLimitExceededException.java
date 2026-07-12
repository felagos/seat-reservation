package com.example.demo.exception;

/**
 * Client would exceed the maximum number of concurrently held seats.
 * HTTP 409 Conflict.
 */
public class SeatHoldLimitExceededException extends RuntimeException {
    public SeatHoldLimitExceededException(int maxSeats) {
        super("Cannot hold more than " + maxSeats + " seats at once");
    }
}
