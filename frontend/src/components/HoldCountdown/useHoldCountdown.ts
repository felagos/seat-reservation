import { useEffect, useState } from 'react';
import { HOLD_TTL_SECONDS } from '../../lib/constants';

export function useHoldCountdown(expiresAt: string, totalSeconds: number = HOLD_TTL_SECONDS) {
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

  return { remaining, progress };
}
