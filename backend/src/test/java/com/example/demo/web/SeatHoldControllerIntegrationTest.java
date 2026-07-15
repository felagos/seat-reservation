package com.example.demo.web;

import com.example.demo.exception.SeatNotFoundException;
import com.example.demo.exception.SeatUnavailableException;
import com.example.demo.service.SeatHoldService;
import com.example.demo.web.dto.HoldRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice test: SeatHoldService is mocked and injected via SeatHoldController's
 * constructor, MockMvc runs standalone (no Spring context) with GlobalExceptionHandler
 * wired in manually so error-mapping behavior is still exercised.
 */
class SeatHoldControllerIntegrationTest {
    private SeatHoldService seatHoldService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        seatHoldService = mock(SeatHoldService.class);
        var controller = new SeatHoldController(seatHoldService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void testHoldMultipleSeatsSuccess() throws Exception {
        var responses = List.of(
            new SeatHoldService.SeatHoldResponse(1L, "A", "1", Instant.now().plusSeconds(120)),
            new SeatHoldService.SeatHoldResponse(2L, "A", "2", Instant.now().plusSeconds(120))
        );
        when(seatHoldService.hold(anyList(), anyString())).thenReturn(responses);

        var request = new HoldRequest(List.of(1L, 2L));

        mockMvc.perform(post("/api/seats/hold")
            .header("X-Client-Id", "client-123")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].expiresAt").exists());

        verify(seatHoldService).hold(List.of(1L, 2L), "client-123");
    }

    @Test
    void testHoldAlreadyHeldSeatFails() throws Exception {
        when(seatHoldService.hold(anyList(), anyString()))
            .thenThrow(new SeatUnavailableException(1L));

        var request = new HoldRequest(List.of(1L));

        mockMvc.perform(post("/api/seats/hold")
            .header("X-Client-Id", "client-123")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
    }

    @Test
    void testHoldInvalidSeatId() throws Exception {
        when(seatHoldService.hold(anyList(), anyString()))
            .thenThrow(new SeatNotFoundException(null));

        var request = new HoldRequest(List.of(99999L));

        mockMvc.perform(post("/api/seats/hold")
            .header("X-Client-Id", "client-123")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }
}
