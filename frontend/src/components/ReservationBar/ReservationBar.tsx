import { Toolbar, Button, Box, Snackbar, Alert } from '@mui/material';
import type { Seat } from '../../types';
import styles from './ReservationBar.module.css';

interface ReservationBarProps {
  seats: Seat[];
  onConfirm: () => void;
  loading: boolean;
  error?: string;
  onDismissError: () => void;
}

export function ReservationBar({ seats, onConfirm, loading, error, onDismissError }: ReservationBarProps) {
  const heldSeats = seats.filter((s) => s.heldByMe && s.status === 'HELD');
  const canConfirm = heldSeats.length > 0;

  return (
    <>
      <Box className={styles.bar}>
        <Toolbar className={styles.toolbar}>
          <Box>
            <strong>Selected seats ({heldSeats.length}/8):</strong>
            {heldSeats.length > 0 && (
              <span className={styles.seatList}>
                {heldSeats.map((s) => `${s.rowLabel}${s.seatNumber}`).join(', ')}
              </span>
            )}
          </Box>
          <Button
            variant="contained"
            color="primary"
            onClick={onConfirm}
            disabled={!canConfirm || loading}
            className={styles.confirmButton}
          >
            {loading ? 'Confirming...' : 'Confirm Reservation'}
          </Button>
        </Toolbar>
      </Box>
      <Snackbar open={!!error} autoHideDuration={6000} onClose={onDismissError}>
        <Alert onClose={onDismissError} severity="error" className={styles.alert}>
          {error}
        </Alert>
      </Snackbar>
    </>
  );
}
