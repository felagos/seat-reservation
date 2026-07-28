package com.example.demo.service;

import com.example.demo.domain.Reservation;
import com.example.demo.domain.Seat;
import com.example.demo.domain.SeatStatus;
import com.example.demo.exception.HoldExpiredException;
import com.example.demo.exception.SeatHoldLimitExceededException;
import com.example.demo.exception.SeatNotFoundException;
import com.example.demo.exception.SeatNotOwnedException;
import com.example.demo.exception.SeatUnavailableException;
import com.example.demo.repository.ReservationRepository;
import com.example.demo.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * doXxxTx methods are tested directly (bypassing lockRegistry/selfProvider indirection) since
 * that's where all the branching logic lives; hold/release/confirm are thin wrappers verified
 * separately by checking they delegate through the lock registry and self-provider proxy.
 */
class SeatHoldServiceTest {
    private SeatRepository seatRepository;
    private ReservationRepository reservationRepository;
    private SeatLockRegistry lockRegistry;
    private ApplicationEventPublisher eventPublisher;
    @SuppressWarnings("unchecked")
    private ObjectProvider<SeatHoldService> selfProvider = mock(ObjectProvider.class);
    private SeatHoldService service;

    @BeforeEach
    void setUp() {
        seatRepository = mock(SeatRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        lockRegistry = mock(SeatLockRegistry.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        selfProvider = mock(ObjectProvider.class);
        service = new SeatHoldService(seatRepository, reservationRepository, lockRegistry, eventPublisher, selfProvider);
        when(selfProvider.getObject()).thenReturn(service);
    }

    private Seat seat(Long id, SeatStatus status, String heldBy, Instant heldUntil) {
        Seat s = new Seat("A", String.valueOf(id));
        s.setId(id);
        s.setStatus(status);
        s.setHeldBy(heldBy);
        s.setHeldUntil(heldUntil);
        return s;
    }

    // --- doHoldTx ---

    @Test
    void doHoldTx_seatCountMismatch_throwsNotFound() {
        when(seatRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(seat(1L, SeatStatus.AVAILABLE, null, null)));

        assertThatThrownBy(() -> service.doHoldTx(List.of(1L, 2L), "client-a"))
            .isInstanceOf(SeatNotFoundException.class);
    }

    @Test
    void doHoldTx_availableSeat_becomesHeldAndPublishesEvent() {
        Seat s = seat(1L, SeatStatus.AVAILABLE, null, null);
        when(seatRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(s));
        when(seatRepository.countByHeldByAndStatus("client-a", SeatStatus.HELD)).thenReturn(0L);

        List<SeatHoldService.SeatHoldResponse> result = service.doHoldTx(List.of(1L), "client-a");

        assertThat(s.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(s.getHeldBy()).isEqualTo("client-a");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).seatId()).isEqualTo(1L);
        assertThat(s.getHeldUntil()).isEqualTo(result.get(0).expiresAt());
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void doHoldTx_reservedSeatWithStaleTimestamp_isNotTreatedAsExpiredHeld() {
        Seat s = seat(1L, SeatStatus.RESERVED, null, Instant.now().minusSeconds(10));
        when(seatRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(s));

        assertThatThrownBy(() -> service.doHoldTx(List.of(1L), "client-a"))
            .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void doHoldTx_availableSeatWithStaleHeldByField_stillCountsAsNewGrant() {
        Seat s = seat(1L, SeatStatus.AVAILABLE, "client-a", null);
        when(seatRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(s));
        when(seatRepository.countByHeldByAndStatus("client-a", SeatStatus.HELD)).thenReturn(8L);

        assertThatThrownBy(() -> service.doHoldTx(List.of(1L), "client-a"))
            .isInstanceOf(SeatHoldLimitExceededException.class);
    }

    @Test
    void doHoldTx_expiredHold_treatedAsAvailable() {
        Seat s = seat(1L, SeatStatus.HELD, "other-client", Instant.now().minusSeconds(10));
        when(seatRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(s));
        when(seatRepository.countByHeldByAndStatus("client-a", SeatStatus.HELD)).thenReturn(0L);

        service.doHoldTx(List.of(1L), "client-a");

        assertThat(s.getHeldBy()).isEqualTo("client-a");
    }

    @Test
    void doHoldTx_ownRenewal_doesNotCountAsNewGrant() {
        Seat s = seat(1L, SeatStatus.HELD, "client-a", Instant.now().plusSeconds(60));
        when(seatRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(s));

        service.doHoldTx(List.of(1L), "client-a");

        verify(seatRepository, times(0)).countByHeldByAndStatus(any(), any());
    }

    @Test
    void doHoldTx_heldByOtherClientNotExpired_throwsUnavailable() {
        Seat s = seat(1L, SeatStatus.HELD, "other-client", Instant.now().plusSeconds(60));
        when(seatRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(s));

        assertThatThrownBy(() -> service.doHoldTx(List.of(1L), "client-a"))
            .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void doHoldTx_exceedsPerClientLimit_throwsLimitExceeded() {
        Seat s = seat(1L, SeatStatus.AVAILABLE, null, null);
        when(seatRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(s));
        when(seatRepository.countByHeldByAndStatus("client-a", SeatStatus.HELD)).thenReturn(8L);

        assertThatThrownBy(() -> service.doHoldTx(List.of(1L), "client-a"))
            .isInstanceOf(SeatHoldLimitExceededException.class);
    }

    @Test
    void doHoldTx_atExactLimit_succeeds() {
        Seat s = seat(1L, SeatStatus.AVAILABLE, null, null);
        when(seatRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(s));
        when(seatRepository.countByHeldByAndStatus("client-a", SeatStatus.HELD)).thenReturn(7L);

        service.doHoldTx(List.of(1L), "client-a");

        assertThat(s.getStatus()).isEqualTo(SeatStatus.HELD);
    }

    // --- doReleaseTx ---

    @Test
    void doReleaseTx_seatNotFound_throwsUnavailable() {
        when(seatRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.doReleaseTx(1L, "client-a"))
            .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void doReleaseTx_notHeld_throwsNotOwned() {
        Seat s = seat(1L, SeatStatus.AVAILABLE, null, null);
        when(seatRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.doReleaseTx(1L, "client-a"))
            .isInstanceOf(SeatNotOwnedException.class);
    }

    @Test
    void doReleaseTx_heldByOtherClient_throwsNotOwned() {
        Seat s = seat(1L, SeatStatus.HELD, "other-client", Instant.now().plusSeconds(60));
        when(seatRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.doReleaseTx(1L, "client-a"))
            .isInstanceOf(SeatNotOwnedException.class);
        assertThat(s.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(s.getHeldBy()).isEqualTo("other-client");
    }

    @Test
    void doReleaseTx_notHeldButStaleHeldByMatchesClient_stillThrowsNotOwned() {
        Seat s = seat(1L, SeatStatus.AVAILABLE, "client-a", null);
        when(seatRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.doReleaseTx(1L, "client-a"))
            .isInstanceOf(SeatNotOwnedException.class);
    }

    @Test
    void doReleaseTx_ownedHold_revertsToAvailableAndPublishesEvent() {
        Seat s = seat(1L, SeatStatus.HELD, "client-a", Instant.now().plusSeconds(60));
        when(seatRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(s));

        service.doReleaseTx(1L, "client-a");

        assertThat(s.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(s.getHeldBy()).isNull();
        assertThat(s.getHeldUntil()).isNull();
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    // --- doConfirmTx ---

    @Test
    void doConfirmTx_seatCountMismatch_throwsNotFound() {
        when(seatRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(seat(1L, SeatStatus.HELD, "client-a", Instant.now().plusSeconds(60))));

        assertThatThrownBy(() -> service.doConfirmTx(List.of(1L, 2L), "client-a"))
            .isInstanceOf(SeatNotFoundException.class);
    }

    @Test
    void doConfirmTx_notHeld_throwsUnavailable() {
        Seat s = seat(1L, SeatStatus.AVAILABLE, null, null);
        when(seatRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(s));

        assertThatThrownBy(() -> service.doConfirmTx(List.of(1L), "client-a"))
            .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void doConfirmTx_heldByOtherClient_throwsNotOwned() {
        Seat s = seat(1L, SeatStatus.HELD, "other-client", Instant.now().plusSeconds(60));
        when(seatRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(s));

        assertThatThrownBy(() -> service.doConfirmTx(List.of(1L), "client-a"))
            .isInstanceOf(SeatNotOwnedException.class);
    }

    @Test
    void doConfirmTx_expiredHold_throwsHoldExpired() {
        Seat s = seat(1L, SeatStatus.HELD, "client-a", Instant.now().minusSeconds(10));
        when(seatRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(s));

        assertThatThrownBy(() -> service.doConfirmTx(List.of(1L), "client-a"))
            .isInstanceOf(HoldExpiredException.class);
    }

    @Test
    void doConfirmTx_validHold_createsReservationAndReservesSeats() {
        Seat s = seat(1L, SeatStatus.HELD, "client-a", Instant.now().plusSeconds(60));
        when(seatRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(s));
        Reservation saved = new Reservation("client-a");
        saved.setId(42L);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(saved);

        Reservation result = service.doConfirmTx(List.of(1L), "client-a");

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(s.getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(s.getReservationId()).isEqualTo(42L);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    // --- wrapper delegation (hold/release/confirm) ---

    @Test
    void hold_singleSeat_delegatesThroughLockRegistryAndReturnsFirstResult() {
        Seat s = seat(1L, SeatStatus.AVAILABLE, null, null);
        when(seatRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(s));
        when(seatRepository.countByHeldByAndStatus("client-a", SeatStatus.HELD)).thenReturn(0L);
        when(lockRegistry.withLocks(eq(List.of(1L)), anyLong(), any())).thenAnswer(inv ->
            ((java.util.function.Supplier<?>) inv.getArgument(2)).get()
        );

        SeatHoldService.SeatHoldResponse response = service.hold(1L, "client-a");

        assertThat(response.seatId()).isEqualTo(1L);
    }

    @Test
    void confirm_deduplicatesAndSortsSeatIdsBeforeAcquiringLocksAndReturnsSavedReservation() {
        Seat s1 = seat(1L, SeatStatus.HELD, "client-a", Instant.now().plusSeconds(60));
        Seat s2 = seat(2L, SeatStatus.HELD, "client-a", Instant.now().plusSeconds(60));
        when(seatRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(s1, s2));
        Reservation saved = new Reservation("client-a");
        saved.setId(1L);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(saved);
        when(lockRegistry.withLocks(eq(List.of(1L, 2L)), anyLong(), any())).thenAnswer(inv ->
            ((java.util.function.Supplier<?>) inv.getArgument(2)).get()
        );

        Reservation result = service.confirm(List.of(2L, 1L, 2L), "client-a");

        verify(lockRegistry).withLocks(eq(List.of(1L, 2L)), anyLong(), any());
        assertThat(result).isSameAs(saved);
    }

    @Test
    void release_delegatesThroughLockRegistryAndSelfProviderToDoReleaseTx() {
        Seat s = seat(1L, SeatStatus.HELD, "client-a", Instant.now().plusSeconds(60));
        when(seatRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(s));
        when(lockRegistry.withLocks(eq(List.of(1L)), anyLong(), any())).thenAnswer(inv ->
            ((java.util.function.Supplier<?>) inv.getArgument(2)).get()
        );

        service.release(1L, "client-a");

        assertThat(s.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }
}
