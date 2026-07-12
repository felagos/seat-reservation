import { Tooltip } from '@mui/material';
import type { Seat as SeatType } from '../../types';
import styles from './Seat.module.css';

interface SeatProps {
  seat: SeatType;
  onHold: (seatId: number) => void;
  onRelease: (seatId: number) => void;
  mutationPending?: boolean;
  blockNewHold?: boolean;
}

export function Seat({ seat, onHold, onRelease, mutationPending, blockNewHold }: SeatProps) {
  const label = `${seat.rowLabel}${seat.seatNumber}`;
  const heldByOther = seat.status === 'HELD' && !seat.heldByMe;
  const reserved = seat.status === 'RESERVED';
  const chosen = seat.status === 'HELD' && seat.heldByMe;
  const blockedByCap = seat.status === 'AVAILABLE' && blockNewHold;
  const disabled = heldByOther || reserved || mutationPending || blockedByCap;

  const title = reserved
    ? `${label} · reserved`
    : heldByOther
      ? `${label} · held by another client`
      : blockedByCap
        ? `${label} · seat limit reached`
        : label;

  const className = [
    styles.seat,
    heldByOther && styles.pending,
    reserved && styles.reserved,
    chosen && styles.chosen,
  ]
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
