package com.example.demo.smoke;

import com.example.demo.repository.SeatRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatCode;

@Tag("smoke")
@SpringBootTest
@ActiveProfiles("test")
class DatabaseConnectivitySmokeTest {

    @Autowired
    private SeatRepository seatRepository;

    @Test
    void seatRepositoryRespondsWithoutError() {
        assertThatCode(seatRepository::count).doesNotThrowAnyException();
    }
}
