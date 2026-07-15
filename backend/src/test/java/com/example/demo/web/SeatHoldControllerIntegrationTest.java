package com.example.demo.web;

import com.example.demo.config.TestRedisConfiguration;
import com.example.demo.domain.Seat;
import com.example.demo.domain.SeatStatus;
import com.example.demo.repository.SeatRepository;
import com.example.demo.web.dto.HoldRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestRedisConfiguration.class)
@Transactional
@Disabled("Requires Redis setup. Run with embedded Redis or remove Redis dependency from test profile.")
class SeatHoldControllerIntegrationTest {
    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        for (int i = 1; i <= 10; i++) {
            var seat = new Seat("A", String.valueOf(i));
            seatRepository.save(seat);
        }
    }

    @Test
    void testHoldMultipleSeatsSuccess() throws Exception {
        var seats = seatRepository.findAll().subList(0, 2);
        var seatIds = seats.stream().map(Seat::getId).toList();
        var request = new HoldRequest(seatIds);

        mockMvc.perform(post("/api/seats/hold")
            .header("X-Client-Id", "client-123")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].expiresAt").exists());

        for (var seat : seats) {
            var updatedSeat = seatRepository.findById(seat.getId()).get();
            assertThat(updatedSeat.getStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(updatedSeat.getHeldBy()).isEqualTo("client-123");
        }
    }

    @Test
    void testHoldAlreadyHeldSeatFails() throws Exception {
        var seat = seatRepository.findAll().get(0);
        seat.setStatus(SeatStatus.HELD);
        seat.setHeldBy("other-client");
        seat.setHeldUntil(Instant.now().plusSeconds(60));
        seatRepository.save(seat);

        var request = new HoldRequest(List.of(seat.getId()));

        mockMvc.perform(post("/api/seats/hold")
            .header("X-Client-Id", "client-123")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
    }

    @Test
    void testHoldInvalidSeatId() throws Exception {
        var request = new HoldRequest(List.of(99999L));

        mockMvc.perform(post("/api/seats/hold")
            .header("X-Client-Id", "client-123")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
    }
}
