import { useMutation } from '@tanstack/react-query';
import { releaseSeat } from '../lib/api';

interface Options {
  onError?: (error: Error) => void;
}

export function useReleaseSeatMutation(eventId: number, options?: Options) {
  return useMutation({
    mutationFn: (seatId: number) => releaseSeat(eventId, seatId),
    onError: (error: Error) => options?.onError?.(error),
  });
}
