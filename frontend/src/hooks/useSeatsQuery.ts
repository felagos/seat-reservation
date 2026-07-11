import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getSeatMap } from '../lib/api';
import { seatsKey } from '../lib/queryKeys';
import { useSeatsStore } from '../store/useSeatsStore';

export function useSeatsQuery(eventId: number) {
  const setSeats = useSeatsStore((s) => s.setSeats);
  const query = useQuery({
    queryKey: seatsKey(eventId),
    queryFn: () => getSeatMap(eventId),
  });

  useEffect(() => {
    if (query.data) {
      setSeats(query.data);
    }
  }, [query.data, setSeats]);

  return query;
}
