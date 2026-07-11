import { Box, CircularProgress, Container, Typography } from '@mui/material';
import { SeatMap } from './components/SeatMap/SeatMap';
import { ReservationBar } from './components/ReservationBar/ReservationBar';
import { useSeatsQuery } from './hooks/useSeatsQuery';
import { useSeatStream } from './hooks/useSeatStream';
import { useHoldSeatMutation } from './hooks/useHoldSeatMutation';
import { useReleaseSeatMutation } from './hooks/useReleaseSeatMutation';
import { useConfirmReservationMutation } from './hooks/useConfirmReservationMutation';
import { useUIStore } from './store/useUIStore';
import { useSeatsStore } from './store/useSeatsStore';
import styles from './App.module.css';

const EVENT_ID = 1;

function App() {
  const { isLoading } = useSeatsQuery(EVENT_ID);
  const seats = useSeatsStore((s) => s.seats);
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
      <Box className={styles.page}>
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
          <CircularProgress sx={{ color: 'primary.main' }} />
        </Box>
      </Box>
    );
  }

  return (
    <Box className={styles.page}>
      <header className={styles.header}>
        <Box className={styles.brand}>
          <Box className={styles.logo}>
            <Box className={styles.logoDot} />
          </Box>
          <Typography component="span" className={styles.brandName}>
            AURORA CINE
          </Typography>
        </Box>
      </header>

      <Container maxWidth="lg" component="main" sx={{ pt: { xs: 3, md: 5 }, pb: 12 }}>
        <SeatMap seats={seats} onHold={handleHold} onRelease={handleRelease} />
      </Container>

      <ReservationBar
        seats={seats}
        onConfirm={handleConfirm}
        loading={confirmMutation.isPending}
        error={error}
        onDismissError={clearError}
      />
    </Box>
  );
}

export default App;
