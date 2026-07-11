import { Grid, Box, Typography } from '@mui/material';
import type { Seat as SeatType } from '../../types';
import { Seat } from '../Seat/Seat';
import styles from './SeatMap.module.css';

interface SeatMapProps {
  seats: SeatType[];
  onHold: (seatId: number) => void;
  onRelease: (seatId: number) => void;
}

export function SeatMap({ seats, onHold, onRelease }: SeatMapProps) {
  const grouped = seats.reduce((acc, seat) => {
    if (!acc[seat.rowLabel]) {
      acc[seat.rowLabel] = [];
    }
    acc[seat.rowLabel].push(seat);
    return acc;
  }, {} as Record<string, SeatType[]>);

  const rows = Object.keys(grouped).sort();

  return (
    <Box className={styles.container}>
      <Typography variant="h5" className={styles.stageLabel}>
        STAGE
      </Typography>
      {rows.map((row) => (
        <Box key={row} className={styles.rowBlock}>
          <Box className={styles.rowContent}>
            <Typography variant="subtitle2" className={styles.rowLabel}>
              Row {row}
            </Typography>
            <Grid container spacing={1}>
              {grouped[row]
                .sort((a, b) => parseInt(a.seatNumber) - parseInt(b.seatNumber))
                .map((seat) => (
                  <Grid item key={seat.id}>
                    <Seat seat={seat} onHold={onHold} onRelease={onRelease} />
                  </Grid>
                ))}
            </Grid>
          </Box>
        </Box>
      ))}
    </Box>
  );
}
