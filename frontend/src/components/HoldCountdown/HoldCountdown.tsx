import { Box, Typography, LinearProgress } from '@mui/material';
import { HOLD_TTL_SECONDS } from '../../lib/constants';
import { useHoldCountdown } from './useHoldCountdown';
import styles from './HoldCountdown.module.css';

interface HoldCountdownProps {
  expiresAt: string;
  totalSeconds?: number;
}

export function HoldCountdown({ expiresAt, totalSeconds = HOLD_TTL_SECONDS }: HoldCountdownProps) {
  const { remaining, progress } = useHoldCountdown(expiresAt, totalSeconds);

  return (
    <Box className={styles.container}>
      <Typography variant="caption" className={styles.label}>
        Expires in {remaining}s
      </Typography>
      <LinearProgress variant="determinate" value={progress} />
    </Box>
  );
}
