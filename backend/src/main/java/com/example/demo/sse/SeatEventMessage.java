package com.example.demo.sse;

public record SeatEventMessage(Long eventId, String eventName, Object payload) {}
