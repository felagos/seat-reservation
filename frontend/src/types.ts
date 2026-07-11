export type SeatStatus = 'AVAILABLE' | 'HELD' | 'RESERVED';

export interface Seat {
  id: number;
  rowLabel: string;
  seatNumber: string;
  status: SeatStatus;
  heldByMe: boolean;
  expiresAt?: string;
}

export interface SeatHeldPayload {
  seatId: number;
  heldBy: string;
  expiresAt: string;
}

export interface SeatReleasedPayload {
  seatId: number;
}

export interface SeatReservedPayload {
  seatId: number;
  reservationId: number;
}

export interface Reservation {
  id: number;
  holderId: string;
  status: string;
}
