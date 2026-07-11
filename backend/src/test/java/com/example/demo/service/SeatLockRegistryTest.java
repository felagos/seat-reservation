package com.example.demo.service;

import com.example.demo.exception.SeatLockTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatLockRegistryTest {
    private SeatLockRegistry lockRegistry;

    @BeforeEach
    void setUp() {
        lockRegistry = new SeatLockRegistry();
    }

    @Test
    void testAcquireLockSuccessfully() {
        var result = lockRegistry.withLocks(List.of(1L), 1000, () -> "success");
        assertThat(result).isEqualTo("success");
    }

    @Test
    void testLockTimeoutThrowsException() throws InterruptedException {
        var blockLatch = new java.util.concurrent.CountDownLatch(1);
        var releaseLatch = new java.util.concurrent.CountDownLatch(1);

        var thread = new Thread(() -> {
            lockRegistry.withLocks(List.of(1L), 5000, () -> {
                blockLatch.countDown();
                try {
                    releaseLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });
        thread.start();

        blockLatch.await();
        try {
            assertThatThrownBy(() ->
                lockRegistry.withLocks(List.of(1L), 100, () -> "should-not-execute")
            ).isInstanceOf(SeatLockTimeoutException.class);
        } finally {
            releaseLatch.countDown();
            thread.join();
        }
    }

    @Test
    void testMultipleLocksAcquiredInOrder() {
        var result = lockRegistry.withLocks(List.of(1L, 2L, 3L), 1000, () -> "multi-lock");
        assertThat(result).isEqualTo("multi-lock");
    }

    @Test
    void testLocksReleasedOnException() {
        var lock1 = lockRegistry.getLock(1L);
        assertThat(lock1.isLocked()).isFalse();

        assertThatThrownBy(() ->
            lockRegistry.withLocks(List.of(1L), 1000, () -> {
                throw new RuntimeException("test error");
            })
        ).isInstanceOf(RuntimeException.class);

        assertThat(lock1.isLocked()).isFalse();
    }

    @Test
    void testConcurrentLockAccess() throws InterruptedException {
        var result1 = new AtomicInteger(0);
        var result2 = new AtomicInteger(0);
        var latch = new CountDownLatch(2);

        var t1 = new Thread(() -> {
            lockRegistry.withLocks(List.of(1L), 5000, () -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                result1.set(1);
                return null;
            });
            latch.countDown();
        });

        var t2 = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            lockRegistry.withLocks(List.of(1L), 5000, () -> {
                result2.set(2);
                return null;
            });
            latch.countDown();
        });

        t1.start();
        t2.start();
        latch.await();

        assertThat(result1.get()).isEqualTo(1);
        assertThat(result2.get()).isEqualTo(2);
    }

    @Test
    void testPartialLockAcquisitionRollback() throws InterruptedException {
        var blockLatch = new java.util.concurrent.CountDownLatch(1);
        var releaseLatch = new java.util.concurrent.CountDownLatch(1);

        var thread = new Thread(() -> {
            lockRegistry.withLocks(List.of(2L), 5000, () -> {
                blockLatch.countDown();
                try {
                    releaseLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });
        thread.start();

        blockLatch.await();
        try {
            assertThatThrownBy(() ->
                lockRegistry.withLocks(List.of(1L, 2L, 3L), 100, () -> "should-fail")
            ).isInstanceOf(SeatLockTimeoutException.class);

            var lock1 = lockRegistry.getLock(1L);
            var lock3 = lockRegistry.getLock(3L);
            assertThat(lock1.tryLock(10, java.util.concurrent.TimeUnit.MILLISECONDS)).isTrue();
            assertThat(lock3.tryLock(10, java.util.concurrent.TimeUnit.MILLISECONDS)).isTrue();
            lock1.unlock();
            lock3.unlock();
        } finally {
            releaseLatch.countDown();
            thread.join();
        }
    }
}
