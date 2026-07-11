package com.example.demo.service;

import com.example.demo.exception.SeatLockTimeoutException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class SeatLockRegistry {
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock getLock(Long seatId) {
        return locks.computeIfAbsent(seatId, key -> new ReentrantLock());
    }

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
