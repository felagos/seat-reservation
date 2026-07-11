import { Chip, Tooltip, Box } from '@mui/material';
import EventSeatIcon from '@mui/icons-material/EventSeat';
import type { Seat as SeatType } from '../../types';
import styles from './Seat.module.css';

interface SeatProps {
  seat: SeatType;
  onHold: (seatId: number) => void;
  onRelease: (seatId: number) => void;
}

export function Seat({ seat, onHold, onRelease }: SeatProps) {
  const getColor = (): 'primary' | 'success' | 'default' | 'error' | 'warning' | 'info' => {
    if (seat.status === 'AVAILABLE') return 'success';
    if (seat.status === 'HELD' && seat.heldByMe) return 'warning';
    if (seat.status === 'HELD') return 'default';
    if (seat.status === 'RESERVED') return 'error';
    return 'default';
  };

  const label = `${seat.rowLabel}${seat.seatNumber}`;
  const disabled = seat.status === 'RESERVED' || (seat.status === 'HELD' && !seat.heldByMe);

  const handleClick = () => {
    if (seat.heldByMe) {
      onRelease(seat.id);
    } else if (seat.status === 'AVAILABLE') {
      onHold(seat.id);
    }
  };

  const chipClassName = [seat.heldByMe && styles.held, disabled && !seat.heldByMe && styles.dimmed]
    .filter(Boolean)
    .join(' ');

  return (
    <Tooltip title={label}>
      <Box className={disabled ? styles.disabled : styles.wrapper}>
        <Chip
          icon={<EventSeatIcon />}
          label={label}
          color={getColor()}
          onClick={handleClick}
          disabled={disabled && !seat.heldByMe}
          variant={seat.heldByMe ? 'filled' : 'outlined'}
          className={chipClassName}
        />
      </Box>
    </Tooltip>
  );
}
