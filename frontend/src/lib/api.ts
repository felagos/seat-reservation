import type { Seat, Reservation } from '../types';
import { getOrCreateClientId } from './clientId';

// Development: use Vite proxy to /api -> localhost:8080
// Production/Docker: set VITE_API_URL=http://backend:8080/api or your backend URL
const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';
const clientId = getOrCreateClientId();

const headers = {
  'X-Client-Id': clientId,
  'Content-Type': 'application/json',
};

export async function getSeatMap(eventId: number): Promise<Seat[]> {
  const response = await fetch(`${BASE_URL}/events/${eventId}/seats`, {
    headers,
  });
  if (!response.ok) throw new Error('Failed to fetch seat map');
  return response.json();
}

export async function holdSeat(eventId: number, seatId: number): Promise<any> {
  const response = await fetch(`${BASE_URL}/events/${eventId}/seats/${seatId}/hold`, {
    method: 'POST',
    headers,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || 'Failed to hold seat');
  }
  return response.json();
}

export async function holdMultipleSeats(eventId: number, seatIds: number[]): Promise<any> {
  const response = await fetch(`${BASE_URL}/events/${eventId}/seats/hold`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ seatIds }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || 'Failed to hold seats');
  }
  return response.json();
}

export async function releaseSeat(eventId: number, seatId: number): Promise<void> {
  const response = await fetch(`${BASE_URL}/events/${eventId}/seats/${seatId}/hold`, {
    method: 'DELETE',
    headers,
  });
  if (!response.ok) throw new Error('Failed to release seat');
}

export async function confirmReservation(eventId: number, seatIds: number[]): Promise<Reservation> {
  const response = await fetch(`${BASE_URL}/events/${eventId}/reservations`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ seatIds }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || 'Failed to confirm reservation');
  }
  return response.json();
}

export async function initDemo(): Promise<void> {
  const response = await fetch(`${BASE_URL}/admin/init-demo`, {
    method: 'POST',
  });
  if (!response.ok) throw new Error('Failed to init demo');
}
