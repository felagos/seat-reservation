import { useQuery } from '@tanstack/react-query';
import { getSeatMap } from '../lib/api';
import { seatsKey } from '../lib/queryKeys';

export function useSeatsQuery() {
  return useQuery({
    queryKey: seatsKey,
    queryFn: () => getSeatMap(),
  });
}
