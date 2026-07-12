package com.example.demo.web.dto;

/**
 * Structured error body returned by GlobalExceptionHandler.
 * Replaces raw exception messages so internals never leak to clients.
 */
public record ErrorResponse(String code, String message, Long seatId) {}
