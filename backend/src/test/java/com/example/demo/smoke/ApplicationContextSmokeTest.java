package com.example.demo.smoke;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("smoke")
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextSmokeTest {

    @Test
    void contextLoads() {
        // If this fails, Spring couldn't wire the app: bad bean config, missing
        // properties, startup errors, etc. The most basic smoke test there is.
    }
}
