package com.example.demo.repository;

import com.example.demo.domain.Seat;
import com.example.demo.domain.SeatStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Data access repository for Seat entities.
 * Provides pessimistic lock queries (FOR UPDATE) for concurrency control.
 * Lock timeout 3000ms configured in @QueryHint.
 */
public interface SeatRepository extends JpaRepository<Seat, Long> {
    /**
     * Get all seats for event, sorted by row label then seat number.
     *
     * @param eventId event ID
     * @return ordered list of seats
     */
    List<Seat> findByEventIdOrderByRowLabelAscSeatNumberAsc(Long eventId);

    /**
     * Get single seat with pessimistic write lock (FOR UPDATE).
     * Timeout 3000ms. Used by hold/release/confirm/sweep operations.
     *
     * @param id seat ID
     * @return seat if found
     * @throws jakarta.persistence.LockTimeoutException if lock not acquired within timeout
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") Long id);

    /**
     * Get multiple seats with pessimistic write lock (FOR UPDATE), sorted by ID.
     * Used for multi-seat operations (must sort to avoid deadlock).
     *
     * @param ids sorted list of seat IDs
     * @return locked seats
     * @throws jakarta.persistence.LockTimeoutException if lock not acquired within timeout
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id in :ids order by s.id")
    List<Seat> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    /**
     * Get seats with status and expiration before cutoff time.
     * Used by sweep job to find expired holds.
     *
     * @param status seat status to filter (e.g., HELD)
     * @param cutoff instant cutoff (seats before this are expired)
     * @return matching seats (no lock)
     */
    List<Seat> findByStatusAndHeldUntilBefore(SeatStatus status, Instant cutoff);
}
