# Frontend - Seat Reservation System

React 19 + TypeScript + Vite + Material UI - Responsive UI for real-time seat reservation.

## Features

- ✅ **Real-time sync**: EventSource (SSE) for instant updates
- ✅ **Optimistic UI**: Immediate local changes with reconciliation
- ✅ **Material UI**: Professional components, responsive theme
- ✅ **Hot-reload**: Vite dev server for fast development
- ✅ **TypeScript**: Type-safe end-to-end
- ✅ **Bun**: Ultra-fast package manager
- ✅ **TanStack Query**: Server state management
- ✅ **Zustand**: Lightweight client state
- ✅ **CSS Modules**: Component-scoped styling

## Structure

```
src/
├── components/              → React components (one folder per component)
│   ├── Seat/
│   │   ├── Seat.tsx
│   │   └── Seat.module.css
│   ├── SeatMap/
│   │   ├── SeatMap.tsx
│   │   └── SeatMap.module.css
│   ├── ReservationBar/
│   │   ├── ReservationBar.tsx
│   │   └── ReservationBar.module.css
│   └── HoldCountdown/
│       ├── HoldCountdown.tsx
│       └── HoldCountdown.module.css

├── hooks/                   → Custom hooks
│   ├── useSeatsQuery.ts     # TanStack Query for seat data
│   ├── useHoldSeatMutation.ts
│   ├── useReleaseSeatMutation.ts
│   ├── useConfirmReservationMutation.ts
│   └── useSeatStream.ts     # EventSource + React Query cache sync

├── lib/                     → Utilities
│   ├── api.ts               # Fetch wrappers to backend
│   ├── queryClient.ts       # TanStack Query client
│   ├── queryKeys.ts         # Query key factory
│   └── clientId.ts          # Generated and stored UUID

├── store/                   → State management
│   └── useUIStore.ts        # Zustand for UI errors

├── types.ts                 # Shared TypeScript types
├── theme.ts                 # Material UI theme
├── App.tsx                  # Root component
├── main.tsx                 # React entry point
└── index.css                # Global styles

public/
└── favicon.svg
```

## Development

### Requirements
- Node.js 18+ (or Bun 1.3+)
- Backend running on `localhost:8080`

### Install

```bash
cd frontend
bun install
# or: npm install / yarn install
```

### Dev server

```bash
bun dev
# Opens http://localhost:5173
# Vite proxies /api → http://localhost:8080
```

### Build

```bash
bun run build
# Generates dist/ → ready for production
```

### Lint

```bash
bun run lint
# Runs oxlint (faster ESLint alternative)
```

## Data Flow

### 1. Initialization

```
App mounts
  ↓
useSeatsQuery() → GET /api/seats
  ↓
Seed demo already ran via docker/init-db/init-demo.sql (db-init service)
  ↓
React Query setQueryData with 50 seats
  ↓
SeatMap renders
```

### 2. EventSource (SSE) opens

```
useSeatStream() in App
  ↓
new EventSource('/api/seats/stream')
  ↓
addEventListener('seat-held', ...)
addEventListener('seat-released', ...)
addEventListener('seat-reserved', ...)
  ↓
Update React Query cache via setQueryData
  ↓
Components re-render
```

### 3. User selects a seat

```
Seat.tsx onClick
  ↓
handleHold(seatId)
  ↓
holdMutation.mutate(seatId)
  ↓
POST /api/seats/3/hold
  ↓
Reconcile with response:
  - If 200: OK, server expiration date
  - If 409: revert state, show error in Snackbar
  ↓
In parallel, SSE notifies other clients:
  'seat-held' event arrives → their state updates
```

### 4. User confirms reservation

```
ReservationBar "Confirm" button
  ↓
handleConfirm()
  ↓
confirmMutation.mutate([seatIds])
  ↓
POST /api/reservations { seatIds: [3, 5] }
  ↓
If 200: invalidateQueries → refetch full map
If 409/410: show error, keep local state
  ↓
SSE notifies others: 'seat-reserved'
```

## Components

### `Seat.tsx`
```tsx
<Seat 
  seat={{id: 3, status: 'HELD', heldByMe: true, expiresAt: '...'}}
  onHold={(id) => ...}
  onRelease={(id) => ...}
/>
```

Renders:
- Status = AVAILABLE: Green chip (clickable)
- Status = HELD + heldByMe: Orange chip (clickable to release)
- Status = HELD: Gray chip (disabled)
- Status = RESERVED: Red chip (disabled)

### `SeatMap.tsx`
Groups seats by row, renders grid with `<Seat>` components.

### `HoldCountdown.tsx`
Progress bar counting down from `expiresAt`. Purely cosmetic (server is authority).

### `ReservationBar.tsx`
- Fixed bar at bottom
- Shows selected seats (held-by-me)
- "Confirm Reservation" button enabled only if ≥1 seat
- Snackbar for errors (409, 410, etc.)

### `App.tsx`
Orchestrates everything:
- Initializes and syncs state
- Opens EventSource
- Handles seat clicks
- Confirms reservation

## Hooks

### `useSeatsQuery()`
```ts
const { data: seats = [], isLoading } = useSeatsQuery();

// seats: array of Seat
// isLoading: boolean
// Replaces initial fetch + loading state from useSeatMap
```

### `useHoldSeatMutation(options)`
```ts
const mutation = useHoldSeatMutation({ 
  onError: (err) => setError(err.message) 
});
mutation.mutate(seatId);
```

### `useReleaseSeatMutation(options)`
```ts
const mutation = useReleaseSeatMutation();
mutation.mutate(seatId);
```

### `useConfirmReservationMutation(options)`
```ts
const mutation = useConfirmReservationMutation();
mutation.mutate([3, 5, 7]);
// On success: invalidates seat query, triggers refetch
```

### `useSeatStream()`
```ts
useSeatStream();
// No callbacks needed — updates React Query cache directly
// Handles:
// - Opens EventSource
// - Listens for events by name
// - Updates cache via setQueryData
// - Auto-detects reconnects (error → open) → invalidates
// - Cleans up on unmount
```

## API Client (`lib/api.ts`)

```ts
// All functions send X-Client-Id header
await getSeatMap()              // GET /api/seats
await holdSeat(seatId)          // POST /api/seats/{seatId}/hold
await releaseSeat(seatId)       // DELETE /api/seats/{seatId}/hold
await confirmReservation([...])// POST /api/reservations
```

Base URL: `VITE_API_URL` environment variable (default: `/api` with Vite proxy in dev)

## Environment Variables

`.env`:
```
VITE_API_URL=/api
```

Vite proxy in `vite.config.ts`:
```ts
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  }
}
```

Flow:
1. Frontend on `http://localhost:5173`
2. Client fetch `/api/...` 
3. Vite dev server intercepts
4. Proxies to `http://localhost:8080/api/...`
5. Backend responds

## Material UI Theme (`theme.ts`)

Status colors:
- AVAILABLE → success (green)
- HELD (by me) → warning (orange)
- HELD (by other) → default (gray)
- RESERVED → error (red)

## Types (`types.ts`)

```ts
type SeatStatus = 'AVAILABLE' | 'HELD' | 'RESERVED';

interface Seat {
  id: number;
  rowLabel: string;
  seatNumber: string;
  status: SeatStatus;
  heldByMe: boolean;
  expiresAt?: string; // ISO 8601 timestamp
}

interface Reservation {
  id: number;
  holderId: string;
  status: string;
}
```

## State Management

### React Query (`lib/queryClient.ts`, hooks)
Server state: seat data
- `useSeatsQuery()` reads cache
- Mutations (`useHoldSeatMutation`, etc.) update server and optionally invalidate/refetch
- `useSeatStream()` patches cache on SSE events without network roundtrip
- `setQueryData()` for optimistic/reactive updates

### Zustand (`store/useUIStore.ts`)
Client state: UI errors
- `useUIStore((s) => s.error)` reads error
- `setError()` from mutation `onError` handlers
- `clearError()` from Snackbar dismiss

## Performance

- **Vite**: ~instant HMR for source changes
- **TypeScript**: Fast compilation (tsc -b)
- **Material UI**: Lightweight components, optimized CSS-in-JS
- **EventSource**: Single connection, low overhead vs polling
- **Virtual threads backend**: Handles thousands of SSE connections

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| 404 /api/... | Backend not running or proxy misconfigured | `make backend` in another terminal |
| SSE not connecting | CORS or proxy issue | Check vite.config.ts, browser console |
| Seat orange in both browsers | heldByMe always true | Check App.tsx clientId comparison |
| "X-Client-Id header missing" | API client not sending header | Check lib/api.ts headers |
| Timeout on hold | Backend lock timeout | Increase `seat.lock.timeout-ms` in backend config |
| Hold not visually expiring | Timer is cosmetic only | Refetch in background or use SSE for expiration |

## Build & Deployment

### Development
```bash
bun dev
```

### Production
```bash
bun run build
# Generates dist/
# Deploy dist/ to web server (nginx, Vercel, etc.)

# If backend on same origin (e.g., /api proxy via nginx):
# VITE_API_URL=/api (default, no change)

# If backend on different domain:
# VITE_API_URL=https://api.example.com
```

## Supported Browsers

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- Opera 76+

(Any modern browser with EventSource support)
