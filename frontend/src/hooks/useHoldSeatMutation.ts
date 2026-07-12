import { useMutation, useQueryClient } from '@tanstack/react-query';
import { holdSeat } from '../lib/api';
import { seatsKey } from '../lib/queryKeys';
import type { Seat } from '../types';

interface Options {
  onError?: (error: Error) => void;
}

export function useHoldSeatMutation(eventId: number, options?: Options) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (seatId: number) => holdSeat(eventId, seatId),
    onSuccess: (response, seatId) => {
      queryClient.setQueryData<Seat[]>(seatsKey(eventId), (seats) =>
        seats?.map((s) =>
          s.id === seatId ? { ...s, status: 'HELD', heldByMe: true, expiresAt: response.expiresAt } : s
        )
      );
    },
    onError: (error: Error) => options?.onError?.(error),
  });
}
