import { useMutation, useQueryClient } from '@tanstack/react-query';
import { releaseSeat } from '../lib/api';
import { seatsKey } from '../lib/queryKeys';
import type { Seat } from '../types';

interface Options {
  onError?: (error: Error) => void;
}

export function useReleaseSeatMutation(eventId: number, options?: Options) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (seatId: number) => releaseSeat(eventId, seatId),
    onSuccess: (_data, seatId) => {
      queryClient.setQueryData<Seat[]>(seatsKey(eventId), (seats) =>
        seats?.map((s) => (s.id === seatId ? { ...s, status: 'AVAILABLE', heldByMe: false, expiresAt: undefined } : s))
      );
    },
    onError: (error: Error) => options?.onError?.(error),
  });
}
