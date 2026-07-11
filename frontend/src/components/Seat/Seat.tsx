import { Tooltip } from '@mui/material';
import type { Seat as SeatType } from '../../types';
import styles from './Seat.module.css';

interface SeatProps {
  seat: SeatType;
  onHold: (seatId: number) => void;
  onRelease: (seatId: number) => void;
}

export function Seat({ seat, onHold, onRelease }: SeatProps) {
  const label = `${seat.rowLabel}${seat.seatNumber}`;
  const pending = seat.status === 'HELD' && !seat.heldByMe;
  const reserved = seat.status === 'RESERVED';
  const chosen = seat.status === 'HELD' && seat.heldByMe;
  const disabled = pending || reserved;

  const title = reserved ? `${label} · reserved` : pending ? `${label} · held by another client` : label;

  const className = [styles.seat, pending && styles.pending, reserved && styles.reserved, chosen && styles.chosen]
    .filter(Boolean)
    .join(' ');

  const handleClick = () => {
    if (seat.heldByMe) {
      onRelease(seat.id);
    } else if (seat.status === 'AVAILABLE') {
      onHold(seat.id);
    }
  };

  return (
    <Tooltip title={title}>
      <span>
        <button
          type="button"
          className={className}
          onClick={handleClick}
          disabled={disabled}
          aria-label={title}
        />
      </span>
    </Tooltip>
  );
}
