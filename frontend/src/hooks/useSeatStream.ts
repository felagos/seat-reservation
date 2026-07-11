import { useEffect, useRef, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { SeatHeldPayload, SeatReleasedPayload, SeatReservedPayload } from '../types';
import { getOrCreateClientId } from '../lib/clientId';
import { seatsKey } from '../lib/queryKeys';
import { useSeatsStore } from '../store/useSeatsStore';

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export function useSeatStream(eventId: number) {
  const queryClient = useQueryClient();
  const seatHeld = useSeatsStore((s) => s.seatHeld);
  const seatReleased = useSeatsStore((s) => s.seatReleased);
  const seatReserved = useSeatsStore((s) => s.seatReserved);
  const eventSourceRef = useRef<EventSource | null>(null);
  const hadErrorRef = useRef(false);

  const setupEventSource = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    const eventSource = new EventSource(`${BASE_URL}/events/${eventId}/stream`);
    eventSourceRef.current = eventSource;
    hadErrorRef.current = false;

    eventSource.addEventListener('seat-held', (event: MessageEvent) => {
      const payload = JSON.parse(event.data) as SeatHeldPayload;
      seatHeld(payload, getOrCreateClientId());
    });

    eventSource.addEventListener('seat-released', (event: MessageEvent) => {
      const payload = JSON.parse(event.data) as SeatReleasedPayload;
      seatReleased(payload);
    });

    eventSource.addEventListener('seat-reserved', (event: MessageEvent) => {
      const payload = JSON.parse(event.data) as SeatReservedPayload;
      seatReserved(payload);
    });

    eventSource.addEventListener('open', () => {
      if (hadErrorRef.current) {
        queryClient.invalidateQueries({ queryKey: seatsKey(eventId) });
        hadErrorRef.current = false;
      }
    });

    eventSource.addEventListener('error', () => {
      hadErrorRef.current = true;
    });
  }, [eventId, queryClient, seatHeld, seatReleased, seatReserved]);

  useEffect(() => {
    setupEventSource();

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
    };
  }, [setupEventSource]);
}
