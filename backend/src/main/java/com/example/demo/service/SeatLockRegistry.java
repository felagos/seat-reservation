package com.example.demo.service;

import com.example.demo.exception.SeatLockTimeoutException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * In-memory lock registry for per-JVM concurrency coordination.
 * Holds a ReentrantLock per seat ID, reference-counted so entries are evicted once no thread
 * is holding or waiting on them — without this, requesting locks for nonexistent/arbitrary seat
 * IDs (validated elsewhere, but defense in depth) would grow the map unboundedly.
 * Fail-fast timeout on tryLock, applied as a single deadline across all locks in a request
 * rather than per-lock, to prevent an N-seat request from waiting up to N * timeoutMs.
 * Cross-instance correctness relies on DB pessimistic locks (SELECT ... FOR UPDATE), not this registry.
 */
@Component
public class SeatLockRegistry {
    private static final class LockHolder {
        final ReentrantLock lock = new ReentrantLock();
        final AtomicInteger refCount = new AtomicInteger(0);
    }

    private final ConcurrentHashMap<Long, LockHolder> locks = new ConcurrentHashMap<>();

    /**
     * Get or create the lock for a seat ID. Intended for inspection/tests; production code
     * should go through {@link #withLocks}, which pairs every acquisition with a release.
     *
     * @param seatId seat identifier
     * @return lock instance for this seat (created if not exists)
     */
    public ReentrantLock getLock(Long seatId) {
        return acquireHolder(seatId).lock;
    }

    /**
     * Acquire multiple locks (in sorted order, deadlock-free), execute action, release all.
     * A single deadline is shared across all lock acquisitions in this call.
     * Reverse-order release prevents deadlock.
     *
     * @param <T> return type of action
     * @param sortedSeatIds pre-sorted list of seat IDs (must be sorted ascending to avoid deadlock)
     * @param timeoutMs total timeout in milliseconds for acquiring all locks
     * @param action supplier that executes with all locks held
     * @return result of action
     * @throws SeatLockTimeoutException if any lock not acquired within the deadline
     */
    public <T> T withLocks(List<Long> sortedSeatIds, long timeoutMs, Supplier<T> action) {
        List<Long> acquiredIds = new ArrayList<>();
        List<LockHolder> acquiredHolders = new ArrayList<>();
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try {
            for (Long seatId : sortedSeatIds) {
                LockHolder holder = acquireHolder(seatId);
                long remainingNanos = deadlineNanos - System.nanoTime();
                boolean locked;
                try {
                    locked = remainingNanos > 0 && holder.lock.tryLock(remainingNanos, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    releaseUnacquired(seatId, holder);
                    Thread.currentThread().interrupt();
                    throw new SeatLockTimeoutException(seatId);
                }
                if (!locked) {
                    releaseUnacquired(seatId, holder);
                    throw new SeatLockTimeoutException(seatId);
                }
                acquiredIds.add(seatId);
                acquiredHolders.add(holder);
            }
            return action.get();
        } finally {
            for (int i = acquiredIds.size() - 1; i >= 0; i--) {
                Long seatId = acquiredIds.get(i);
                LockHolder holder = acquiredHolders.get(i);
                holder.lock.unlock();
                release(seatId, holder);
            }
        }
    }

    /**
     * Get-or-create the holder for a seat ID and register this thread's interest in it.
     * Increment happens atomically with the map lookup (via compute) so it can never race
     * with another thread's {@link #release} evicting the same entry.
     */
    private LockHolder acquireHolder(Long seatId) {
        LockHolder[] result = new LockHolder[1];
        locks.compute(seatId, (key, existing) -> {
            LockHolder holder = existing != null ? existing : new LockHolder();
            holder.refCount.incrementAndGet();
            result[0] = holder;
            return holder;
        });
        return result[0];
    }

    /** Drop this thread's interest in a holder it never locked (timed out or interrupted). */
    private void releaseUnacquired(Long seatId, LockHolder holder) {
        release(seatId, holder);
    }

    /** Drop this thread's interest in a holder; evict it once no one else references it. */
    private void release(Long seatId, LockHolder holder) {
        locks.compute(seatId, (key, existing) -> holder.refCount.decrementAndGet() == 0 ? null : existing);
    }
}
