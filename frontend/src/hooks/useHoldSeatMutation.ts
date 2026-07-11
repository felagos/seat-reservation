import { useMutation } from '@tanstack/react-query';
import { holdSeat } from '../lib/api';

interface Options {
  onError?: (error: Error) => void;
}

export function useHoldSeatMutation(eventId: number, options?: Options) {
  return useMutation({
    mutationFn: (seatId: number) => holdSeat(eventId, seatId),
    onError: (error: Error) => options?.onError?.(error),
  });
}
