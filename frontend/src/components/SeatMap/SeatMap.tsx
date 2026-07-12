import { useMemo } from 'react';
import { Grid2 as Grid, Box, Typography } from '@mui/material';
import type { Seat as SeatType } from '../../types';
import { Seat } from '../Seat/Seat';
import { MAX_SEATS_PER_HOLD } from '../../lib/constants';
import styles from './SeatMap.module.css';

interface SeatMapProps {
  seats: SeatType[];
  onHold: (seatId: number) => void;
  onRelease: (seatId: number) => void;
  pendingSeatId?: number;
  maxSeatsReached: boolean;
}

const LEGEND = [
  { label: 'Available', swatchClass: styles.swatchAvailable },
  { label: 'Selected', swatchClass: styles.swatchSelected },
  { label: 'Held by other', swatchClass: styles.swatchPending },
  { label: 'Reserved', swatchClass: styles.swatchReserved },
];

export function SeatMap({ seats, onHold, onRelease, pendingSeatId, maxSeatsReached }: SeatMapProps) {
  const rows = useMemo(() => {
    const grouped = seats.reduce((acc, seat) => {
      if (!acc[seat.rowLabel]) {
        acc[seat.rowLabel] = [];
      }
      acc[seat.rowLabel].push(seat);
      return acc;
    }, {} as Record<string, SeatType[]>);

    return Object.keys(grouped)
      .sort()
      .map((row) => ({
        row,
        seats: [...grouped[row]].sort((a, b) => parseInt(a.seatNumber, 10) - parseInt(b.seatNumber, 10)),
      }));
  }, [seats]);

  return (
    <Box className={styles.container}>
      <Typography variant="h5" className={styles.title}>
        Choose your seats
      </Typography>
      <Typography className={styles.subtitle}>Select up to {MAX_SEATS_PER_HOLD} seats for this showing.</Typography>

      <Box className={styles.mapScroll}>
        <Box className={styles.screenBar}>STAGE</Box>

        <Box className={styles.seatMapWrap}>
          {rows.map(({ row, seats: rowSeats }) => (
            <Box key={row} className={styles.rowBlock}>
              <Typography component="span" className={styles.rowLabel}>
                {row}
              </Typography>
              <Grid container spacing={1} className={styles.rowSeats}>
                {rowSeats.map((seat) => (
                  <Grid key={seat.id}>
                    <Seat
                      seat={seat}
                      onHold={onHold}
                      onRelease={onRelease}
                      mutationPending={seat.id === pendingSeatId}
                      blockNewHold={maxSeatsReached}
                    />
                  </Grid>
                ))}
              </Grid>
            </Box>
          ))}
        </Box>
      </Box>

      <Box className={styles.legend}>
        {LEGEND.map((li) => (
          <Box key={li.label} className={styles.legendItem}>
            <span className={`${styles.swatch} ${li.swatchClass}`} />
            {li.label}
          </Box>
        ))}
      </Box>
    </Box>
  );
}
