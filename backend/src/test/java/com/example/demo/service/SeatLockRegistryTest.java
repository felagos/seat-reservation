package com.example.demo.service;

import com.example.demo.exception.SeatLockTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
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

    /** Reads the private lock map directly to verify holder entries are evicted (or kept) as expected. */
    private int lockMapSize() throws ReflectiveOperationException {
        Field field = SeatLockRegistry.class.getDeclaredField("locks");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(lockRegistry)).size();
    }

    @Test
    void testAcquireLockSuccessfully() throws Exception {
        var result = lockRegistry.withLocks(List.of(1L), 1000, () -> "success");
        assertThat(result).isEqualTo("success");
        assertThat(lockMapSize()).isZero();
    }

    @Test
    void testLockTimeoutThrowsException() throws Exception {
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
        assertThat(lockMapSize()).isZero();
    }

    @Test
    void testHolderNotEvictedWhileAnotherThreadStillWaiting() throws Exception {
        var holdingLatch = new CountDownLatch(1);
        var releaseLatch = new CountDownLatch(1);
        var waiterDone = new CountDownLatch(1);

        var holder = new Thread(() -> lockRegistry.withLocks(List.of(1L), 5000, () -> {
            holdingLatch.countDown();
            try {
                releaseLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        holder.start();
        holdingLatch.await();

        var waiter = new Thread(() -> {
            lockRegistry.withLocks(List.of(1L), 5000, () -> "waited");
            waiterDone.countDown();
        });
        waiter.start();
        Thread.sleep(150); // let waiter register its interest (increment refCount) and block on tryLock

        assertThat(lockMapSize()).isEqualTo(1);

        releaseLatch.countDown();
        holder.join();
        waiterDone.await();
        waiter.join();

        assertThat(lockMapSize()).isZero();
    }

    @Test
    void testReleaseUnacquiredOnInterrupt_decrementsRefCount() throws Exception {
        var holdingLatch = new CountDownLatch(1);
        var releaseLatch = new CountDownLatch(1);

        var holder = new Thread(() -> lockRegistry.withLocks(List.of(1L), 5000, () -> {
            holdingLatch.countDown();
            try {
                releaseLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        holder.start();
        holdingLatch.await();

        var caughtTimeout = new AtomicInteger(0);
        var interruptFlagRestored = new AtomicInteger(0);
        var waiter = new Thread(() -> {
            try {
                lockRegistry.withLocks(List.of(1L), 5000, () -> "should-not-run");
            } catch (SeatLockTimeoutException e) {
                caughtTimeout.incrementAndGet();
                if (Thread.currentThread().isInterrupted()) {
                    interruptFlagRestored.incrementAndGet();
                }
            }
        });
        waiter.start();
        Thread.sleep(150); // let waiter register its interest and block on tryLock
        waiter.interrupt();
        waiter.join();

        assertThat(caughtTimeout.get()).isEqualTo(1);
        assertThat(interruptFlagRestored.get()).isEqualTo(1);
        assertThat(lockMapSize()).isEqualTo(1); // holder thread still holds/references it

        releaseLatch.countDown();
        holder.join();

        assertThat(lockMapSize()).isZero();
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
