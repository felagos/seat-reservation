package com.example.demo.web;

import com.example.demo.exception.HoldExpiredException;
import com.example.demo.exception.SeatHoldLimitExceededException;
import com.example.demo.exception.SeatLockTimeoutException;
import com.example.demo.exception.SeatNotFoundException;
import com.example.demo.exception.SeatNotOwnedException;
import com.example.demo.exception.SeatUnavailableException;
import com.example.demo.web.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to HTTP status codes and a structured body.
 * Centralizes error handling so controllers stay free of try/catch and never leak raw
 * exception messages (Hibernate/SQL internals) to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SeatNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(SeatNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("SEAT_NOT_FOUND", e.getMessage(), e.getSeatId()));
    }

    @ExceptionHandler(SeatUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(SeatUnavailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("SEAT_UNAVAILABLE", e.getMessage(), e.getSeatId()));
    }

    @ExceptionHandler(SeatLockTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleLockTimeout(SeatLockTimeoutException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("SEAT_LOCK_TIMEOUT", e.getMessage(), e.getSeatId()));
    }

    @ExceptionHandler(SeatNotOwnedException.class)
    public ResponseEntity<ErrorResponse> handleNotOwned(SeatNotOwnedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("SEAT_NOT_OWNED", e.getMessage(), e.getSeatId()));
    }

    @ExceptionHandler(SeatHoldLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleHoldLimitExceeded(SeatHoldLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("SEAT_HOLD_LIMIT_EXCEEDED", e.getMessage(), null));
    }

    @ExceptionHandler(HoldExpiredException.class)
    public ResponseEntity<ErrorResponse> handleHoldExpired(HoldExpiredException e) {
        return ResponseEntity.status(HttpStatus.GONE)
            .body(new ErrorResponse("HOLD_EXPIRED", e.getMessage(), e.getSeatId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
            .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_ERROR", message, null));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("BAD_REQUEST", "Malformed request", null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
            .findFirst()
            .map(v -> v.getPropertyPath() + " " + v.getMessage())
            .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("VALIDATION_ERROR", message, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "Internal server error", null));
    }
}
