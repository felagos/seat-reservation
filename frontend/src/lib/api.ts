import type { Seat, Reservation } from '../types';
import { http } from './http';

export async function getSeatMap(eventId: number): Promise<Seat[]> {
  return http.get(`/events/${eventId}/seats`);
}

export async function holdSeat(eventId: number, seatId: number): Promise<any> {
  return http.post(`/events/${eventId}/seats/${seatId}/hold`);
}

export async function holdMultipleSeats(eventId: number, seatIds: number[]): Promise<any> {
  return http.post(`/events/${eventId}/seats/hold`, { seatIds });
}

export async function releaseSeat(eventId: number, seatId: number): Promise<void> {
  await http.del(`/events/${eventId}/seats/${seatId}/hold`);
}

export async function confirmReservation(eventId: number, seatIds: number[]): Promise<Reservation> {
  return http.post(`/events/${eventId}/reservations`, { seatIds });
}
