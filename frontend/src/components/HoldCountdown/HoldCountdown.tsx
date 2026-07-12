import { useEffect, useState } from 'react';
import { Box, Typography, LinearProgress } from '@mui/material';
import { HOLD_TTL_SECONDS } from '../../lib/constants';
import styles from './HoldCountdown.module.css';

interface HoldCountdownProps {
  expiresAt: string;
  totalSeconds?: number;
}

export function HoldCountdown({ expiresAt, totalSeconds = HOLD_TTL_SECONDS }: HoldCountdownProps) {
  const [remaining, setRemaining] = useState(0);

  useEffect(() => {
    const updateCountdown = () => {
      const expiry = new Date(expiresAt).getTime();
      const now = Date.now();
      const diff = expiry - now;
      setRemaining(Math.max(0, Math.ceil(diff / 1000)));
    };

    updateCountdown();
    const interval = setInterval(updateCountdown, 1000);
    return () => clearInterval(interval);
  }, [expiresAt]);

  const progress = (remaining / totalSeconds) * 100;

  return (
    <Box className={styles.container}>
      <Typography variant="caption" className={styles.label}>
        Expires in {remaining}s
      </Typography>
      <LinearProgress variant="determinate" value={progress} />
    </Box>
  );
}
