package com.example.demo.web.dto;

import java.util.List;

public record ConfirmReservationRequest(List<Long> seatIds) {}
