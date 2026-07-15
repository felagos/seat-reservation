package com.example.demo.service;

import com.example.demo.config.TestRedisConfiguration;
import com.example.demo.domain.Seat;
import com.example.demo.domain.SeatStatus;
import com.example.demo.exception.SeatLockTimeoutException;
import com.example.demo.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfiguration.class)
@Transactional
@Disabled("Requires Redis setup. Run with embedded Redis or remove Redis dependency from test profile.")
class ConcurrencySeatHoldTest {
    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SeatLockRegistry lockRegistry;

    @BeforeEach
    void setUp() {
        for (int i = 1; i <= 20; i++) {
            var seat = new Seat("A", String.valueOf(i));
            seatRepository.save(seat);
        }
    }

    @Test
    void testConcurrentHoldsOnSameSeat() throws InterruptedException {
        var seat = seatRepository.findAll().get(0);
        var successCount = new AtomicInteger(0);
        var failureCount = new AtomicInteger(0);
        var numThreads = 5;
        var latch = new CountDownLatch(numThreads);

        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            for (int i = 0; i < numThreads; i++) {
                var clientId = "client-" + i;
                executor.submit(() -> {
                    try {
                        lockRegistry.withLocks(List.of(seat.getId()), 2000, () -> {
                            var s = seatRepository.findByIdForUpdate(seat.getId()).get();
                            if (s.getStatus() == SeatStatus.AVAILABLE) {
                                s.setStatus(SeatStatus.HELD);
                                s.setHeldBy(clientId);
                                seatRepository.save(s);
                                successCount.incrementAndGet();
                                return null;
                            }
                            failureCount.incrementAndGet();
                            return null;
                        });
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(numThreads - 1);

        var finalSeat = seatRepository.findById(seat.getId()).get();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void testConcurrentHoldsMultipleDifferentSeats() throws InterruptedException {
        var seats = seatRepository.findAll().subList(0, 5);
        var successCount = new AtomicInteger(0);
        var latch = new CountDownLatch(seats.size());

        try (ExecutorService executor = Executors.newFixedThreadPool(seats.size())) {
            for (int i = 0; i < seats.size(); i++) {
                var seat = seats.get(i);
                var clientId = "client-" + i;
                executor.submit(() -> {
                    try {
                        lockRegistry.withLocks(List.of(seat.getId()), 2000, () -> {
                            var s = seatRepository.findByIdForUpdate(seat.getId()).get();
                            s.setStatus(SeatStatus.HELD);
                            s.setHeldBy(clientId);
                            seatRepository.save(s);
                            successCount.incrementAndGet();
                            return null;
                        });
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        assertThat(successCount.get()).isEqualTo(seats.size());
        seats.forEach(seat -> {
            var updated = seatRepository.findById(seat.getId()).get();
            assertThat(updated.getStatus()).isEqualTo(SeatStatus.HELD);
        });
    }

    @Test
    void testLockTimeoutPreventsDamage() throws InterruptedException {
        var seat = seatRepository.findAll().get(0);
        var numThreads = 3;
        var timeoutOccurred = new AtomicInteger(0);
        var latch = new CountDownLatch(numThreads);

        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            var lock = lockRegistry.getLock(seat.getId());
            lock.lock();

            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        lockRegistry.withLocks(List.of(seat.getId()), 50, () -> {
                            return null;
                        });
                    } catch (SeatLockTimeoutException e) {
                        timeoutOccurred.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            lock.unlock();
        }

        assertThat(timeoutOccurred.get()).isEqualTo(numThreads);
        var finalSeat = seatRepository.findById(seat.getId()).get();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }
}
