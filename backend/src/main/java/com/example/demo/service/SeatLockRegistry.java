package com.example.demo.service;

import com.example.demo.exception.SeatLockTimeoutException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * In-memory lock registry for per-JVM concurrency coordination.
 * Holds ReentrantLock per seat ID. Fail-fast timeout on tryLock to prevent hanging transactions.
 * Cross-instance correctness relies on DB pessimistic locks (SELECT ... FOR UPDATE), not this registry.
 */
@Component
public class SeatLockRegistry {
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Get or create ReentrantLock for seat ID.
     *
     * @param seatId seat identifier
     * @return lock instance for this seat (created if not exists)
     */
    public ReentrantLock getLock(Long seatId) {
        return locks.computeIfAbsent(seatId, key -> new ReentrantLock());
    }

    /**
     * Acquire multiple locks (in sorted order, deadlock-free), execute action, release all.
     * Timeout applies to each lock acquisition. Reverse-order release prevents deadlock.
     *
     * @param <T> return type of action
     * @param sortedSeatIds pre-sorted list of seat IDs (must be sorted ascending to avoid deadlock)
     * @param timeoutMs timeout in milliseconds for each lock.tryLock() call
     * @param action supplier that executes with all locks held
     * @return result of action
     * @throws SeatLockTimeoutException if any lock not acquired within timeout
     */
    public <T> T withLocks(List<Long> sortedSeatIds, long timeoutMs, Supplier<T> action) {
        List<Long> acquiredIds = new ArrayList<>();
        try {
            for (Long seatId : sortedSeatIds) {
                ReentrantLock lock = getLock(seatId);
                if (!lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
                    throw new SeatLockTimeoutException(seatId);
                }
                acquiredIds.add(seatId);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SeatLockTimeoutException(sortedSeatIds.get(0));
        } finally {
            for (int i = acquiredIds.size() - 1; i >= 0; i--) {
                getLock(acquiredIds.get(i)).unlock();
            }
        }
    }
}
