import { useQuery } from '@tanstack/react-query';
import { getSeatMap } from '../lib/api';
import { seatsKey } from '../lib/queryKeys';

export function useSeatsQuery(eventId: number) {
  return useQuery({
    queryKey: seatsKey(eventId),
    queryFn: () => getSeatMap(eventId),
  });
}
