import type { Seat, Reservation, SeatHoldResponse } from '../types';
import { http } from './http';

export async function getSeatMap(): Promise<Seat[]> {
  return http.get(`/seats`);
}

export async function holdSeat(seatId: number): Promise<SeatHoldResponse> {
  return http.post(`/seats/${seatId}/hold`);
}

export async function releaseSeat(seatId: number): Promise<void> {
  await http.del(`/seats/${seatId}/hold`);
}

export async function confirmReservation(seatIds: number[]): Promise<Reservation> {
  return http.post(`/reservations`, { seatIds });
}
