package com.example.demo.smoke;

import com.example.demo.domain.Seat;
import com.example.demo.repository.SeatRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Happy-path check on the 3 endpoints a broken deploy would hurt most: browsing seats,
 * and the hold/release pair that exercises the pessimistic-locking write path end to end.
 * Wrapped in a test-managed transaction (rolled back after each test) so it leaves no residue —
 * doHoldTx/doReleaseTx use default REQUIRED propagation, so they join and roll back with it.
 */
@Tag("smoke")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CriticalEndpointsSmokeTest {

    private static final String CLIENT_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    void seatMapEndpointRespondsOk() throws Exception {
        mockMvc.perform(get("/api/seats"))
            .andExpect(status().isOk());
    }

    @Test
    void holdThenReleaseSeatRoundTripsSuccessfully() throws Exception {
        Seat seat = seatRepository.save(new Seat("Z", "1"));

        mockMvc.perform(post("/api/seats/{seatId}/hold", seat.getId())
                .header("X-Client-Id", CLIENT_ID))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/seats/{seatId}/hold", seat.getId())
                .header("X-Client-Id", CLIENT_ID))
            .andExpect(status().isNoContent());
    }
}
