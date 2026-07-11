import { createTheme } from '@mui/material/styles';

const accent = '#e0533d';
const accentGlow = 'rgba(224,83,61,.45)';

export const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: {
      main: accent,
    },
    secondary: {
      main: '#2fd4c4',
    },
    success: {
      main: '#4caf50',
    },
    warning: {
      main: '#f2c94c',
    },
    error: {
      main: '#f44336',
    },
    info: {
      main: '#2196f3',
    },
    background: {
      default: '#120f0e',
      paper: '#1d1613',
    },
    text: {
      primary: '#f5f1ec',
      secondary: 'rgba(245,241,236,.55)',
    },
  },
  typography: {
    fontFamily: '"Inter", sans-serif',
    h1: { fontFamily: '"Space Grotesk", sans-serif' },
    h2: { fontFamily: '"Space Grotesk", sans-serif' },
    h3: { fontFamily: '"Space Grotesk", sans-serif' },
    h4: { fontFamily: '"Space Grotesk", sans-serif' },
    h5: { fontFamily: '"Space Grotesk", sans-serif', fontWeight: 700 },
    h6: { fontFamily: '"Space Grotesk", sans-serif', fontWeight: 700 },
  },
  components: {
    MuiChip: {
      styleOverrides: {
        root: {
          height: 'auto',
          padding: '8px',
        },
      },
    },
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          background:
            'radial-gradient(120% 100% at 50% -10%, #1d1613 0%, #120f0e 55%, #0c0a09 100%)',
        },
        '::selection': {
          background: accentGlow,
        },
      },
    },
  },
});
