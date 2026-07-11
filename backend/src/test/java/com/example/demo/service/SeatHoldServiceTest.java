package com.example.demo.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeatHoldServiceTest {
    @Test
    void serviceWiringTest() {
        var lockRegistry = new SeatLockRegistry();
        assertThat(lockRegistry).isNotNull();
    }
}
