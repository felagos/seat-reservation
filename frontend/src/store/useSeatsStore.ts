import { create } from 'zustand';
import type { Seat, SeatHeldPayload, SeatReleasedPayload, SeatReservedPayload } from '../types';

interface SeatsState {
  seats: Seat[];
  setSeats: (seats: Seat[]) => void;
  seatHeld: (payload: SeatHeldPayload, clientId: string) => void;
  seatReleased: (payload: SeatReleasedPayload) => void;
  seatReserved: (payload: SeatReservedPayload) => void;
}

export const useSeatsStore = create<SeatsState>((set) => ({
  seats: [],
  setSeats: (seats) => set({ seats }),
  seatHeld: (payload, clientId) =>
    set((state) => ({
      seats: state.seats.map((s) =>
        s.id === payload.seatId
          ? { ...s, status: 'HELD', heldByMe: payload.heldBy === clientId, expiresAt: payload.expiresAt }
          : s
      ),
    })),
  seatReleased: (payload) =>
    set((state) => ({
      seats: state.seats.map((s) =>
        s.id === payload.seatId ? { ...s, status: 'AVAILABLE', heldByMe: false, expiresAt: undefined } : s
      ),
    })),
  seatReserved: (payload) =>
    set((state) => ({
      seats: state.seats.map((s) => (s.id === payload.seatId ? { ...s, status: 'RESERVED' } : s)),
    })),
}));
