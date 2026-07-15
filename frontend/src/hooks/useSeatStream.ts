import { useEffect, useRef, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { Seat, SeatHeldPayload, SeatReleasedPayload, SeatReservedPayload } from '../types';
import { seatsKey } from '../lib/queryKeys';

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export function useSeatStream() {
  const queryClient = useQueryClient();
  const eventSourceRef = useRef<EventSource | null>(null);
  const hadErrorRef = useRef(false);

  const patchSeat = useCallback(
    (seatId: number, updater: (seat: Seat) => Seat) => {
      queryClient.setQueryData<Seat[]>(seatsKey, (seats) =>
        seats?.map((s) => (s.id === seatId ? updater(s) : s))
      );
    },
    [queryClient]
  );

  const setupEventSource = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    const eventSource = new EventSource(`${BASE_URL}/seats/stream`);
    eventSourceRef.current = eventSource;
    hadErrorRef.current = false;

    eventSource.addEventListener('seat-held', (event: MessageEvent) => {
      const payload = JSON.parse(event.data) as SeatHeldPayload;
      // heldByMe is never set from the stream (the payload doesn't carry the holder's
      // identity — see backend SeatHeldPayload) — only this client's own hold mutation sets it.
      patchSeat(payload.seatId, (s) => ({ ...s, status: 'HELD', expiresAt: payload.expiresAt }));
    });

    eventSource.addEventListener('seat-released', (event: MessageEvent) => {
      const payload = JSON.parse(event.data) as SeatReleasedPayload;
      patchSeat(payload.seatId, (s) => ({ ...s, status: 'AVAILABLE', heldByMe: false, expiresAt: undefined }));
    });

    eventSource.addEventListener('seat-reserved', (event: MessageEvent) => {
      const payload = JSON.parse(event.data) as SeatReservedPayload;
      patchSeat(payload.seatId, (s) => ({ ...s, status: 'RESERVED', heldByMe: false, expiresAt: undefined }));
    });

    eventSource.addEventListener('open', () => {
      if (hadErrorRef.current) {
        queryClient.invalidateQueries({ queryKey: seatsKey });
        hadErrorRef.current = false;
      }
    });

    eventSource.addEventListener('error', () => {
      hadErrorRef.current = true;
    });
  }, [queryClient, patchSeat]);

  useEffect(() => {
    setupEventSource();

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
    };
  }, [setupEventSource]);
}
