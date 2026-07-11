import { Container, Box, CircularProgress } from '@mui/material';
import { SeatMap } from './components/SeatMap/SeatMap';
import { ReservationBar } from './components/ReservationBar/ReservationBar';
import { useSeatsQuery } from './hooks/useSeatsQuery';
import { useSeatStream } from './hooks/useSeatStream';
import { useHoldSeatMutation } from './hooks/useHoldSeatMutation';
import { useReleaseSeatMutation } from './hooks/useReleaseSeatMutation';
import { useConfirmReservationMutation } from './hooks/useConfirmReservationMutation';
import { useUIStore } from './store/useUIStore';

const EVENT_ID = 1;

function App() {
  const { data: seats = [], isLoading } = useSeatsQuery(EVENT_ID);
  useSeatStream(EVENT_ID);

  const error = useUIStore((s) => s.error);
  const setError = useUIStore((s) => s.setError);
  const clearError = useUIStore((s) => s.clearError);

  const holdMutation = useHoldSeatMutation(EVENT_ID, { onError: (e) => setError(e.message) });
  const releaseMutation = useReleaseSeatMutation(EVENT_ID, { onError: (e) => setError(e.message) });
  const confirmMutation = useConfirmReservationMutation(EVENT_ID, { onError: (e) => setError(e.message) });

  const handleHold = (seatId: number) => {
    holdMutation.mutate(seatId);
  };

  const handleRelease = (seatId: number) => {
    releaseMutation.mutate(seatId);
  };

  const handleConfirm = () => {
    const heldSeats = seats.filter((s) => s.heldByMe && s.status === 'HELD').map((s) => s.id);
    if (heldSeats.length === 0) {
      setError('Please select at least one seat');
      return;
    }
    confirmMutation.mutate(heldSeats);
  };

  if (isLoading) {
    return (
      <Container>
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
          <CircularProgress />
        </Box>
      </Container>
    );
  }

  return (
    <Container maxWidth="lg">
      <Box sx={{ pb: 10 }}>
        <SeatMap seats={seats} onHold={handleHold} onRelease={handleRelease} />
      </Box>
      <ReservationBar
        seats={seats}
        onConfirm={handleConfirm}
        loading={confirmMutation.isPending}
        error={error}
        onDismissError={clearError}
      />
    </Container>
  );
}

export default App;
