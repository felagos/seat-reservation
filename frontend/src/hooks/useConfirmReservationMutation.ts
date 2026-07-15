import { useMutation, useQueryClient } from '@tanstack/react-query';
import { confirmReservation } from '../lib/api';
import { seatsKey } from '../lib/queryKeys';

interface Options {
  onError?: (error: Error) => void;
}

export function useConfirmReservationMutation(options?: Options) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (seatIds: number[]) => confirmReservation(seatIds),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: seatsKey });
    },
    onError: (error: Error) => options?.onError?.(error),
  });
}
