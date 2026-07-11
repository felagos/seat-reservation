# Frontend - Sistema de Reserva de Asientos

React 19 + TypeScript + Vite + Material UI - UI responsiva para reserva de asientos en tiempo real.

## Características

- ✅ **Real-time sync**: EventSource (SSE) para actualizaciones instantáneas
- ✅ **Optimistic UI**: Cambios locales inmediatos con reconciliación
- ✅ **Material UI**: Componentes profesionales, tema responsive
- ✅ **Hot-reload**: Vite dev server para desarrollo rápido
- ✅ **TypeScript**: Tipado seguro end-to-end
- ✅ **Bun**: Package manager ultra-rápido

## Estructura

```
src/
├── components/              → Componentes React
│   ├── Seat.tsx             # Botón individual de asiento
│   ├── SeatMap.tsx          # Grid de asientos por fila
│   ├── HoldCountdown.tsx    # Timer de expiración
│   └── ReservationBar.tsx   # Barra de acción + feedback

├── hooks/                   → Custom hooks
│   ├── useSeatMap.ts        # State + reducers para mapa de asientos
│   └── useSeatStream.ts     # EventSource + sincronización SSE

├── lib/                     → Utilidades
│   ├── api.ts               # Wrappers fetch → backend
│   └── clientId.ts          # UUID generado y almacenado

├── types.ts                 # Tipos TypeScript compartidos
├── theme.ts                 # Tema Material UI
├── App.tsx                  # Componente raíz
├── main.tsx                 # Entry point React
└── index.css                # Estilos globales

public/
└── favicon.svg

.env                         # Variables de entorno
.env.local                   # Overrides locales (git-ignored)
vite.config.ts              # Config de Vite
tsconfig.json               # Config de TypeScript
package.json                # Deps + scripts
bun.lock                     # Lock file (Bun)
```

## Desarrollo

### Requisitos
- Node.js 18+ (o Bun 1.3+)
- Backend corriendo en `localhost:8080`

### Install

```bash
cd frontend
bun install
# o: npm install / yarn install
```

### Dev server

```bash
bun dev
# Abre http://localhost:5173
# Vite proxea /api → http://localhost:8080
```

### Build

```bash
bun run build
# Genera dist/ → listo para production
```

### Lint

```bash
bun run lint
# Ejecuta oxlint (ESLint alternativa, más rápida)
```

## Flujo de datos

### 1. Inicialización

```
App monta con EVENT_ID = 1
  ↓
useSeatMap(1) → GET /api/events/1/seats
  ↓
Seed demo ya corrió vía docker/init-db/init-demo.sql (servicio db-init)
  ↓
useReducer dispatch INIT con 50 asientos
  ↓
SeatMap renderiza
```

### 2. EventSource (SSE) se abre

```
useSeatStream(1, ...) en App
  ↓
new EventSource('/api/events/1/stream')
  ↓
addEventListener('seat-held', ...)
addEventListener('seat-released', ...)
addEventListener('seat-reserved', ...)
  ↓
Actualiza local state vía dispatch
  ↓
Componentes re-renderizan
```

### 3. Usuario selecciona asiento

```
Seat.tsx onClick
  ↓
handleHold(seatId)
  ↓
// Optimistic UI: actualiza estado local inmediatamente
dispatch({ type: 'SEAT_HELD', payload: {...}, clientId: ... })
  ↓
holdSeat(1, seatId) → POST /api/events/1/seats/3/hold
  ↓
// Reconcilia con respuesta:
  - Si 200: OK, fecha de expiración del servidor
  - Si 409: revertir state, mostrar error en Snackbar
  ↓
// En paralelo, SSE notifica a otros clientes:
evento 'seat-held' llega → su estado se actualiza
```

### 4. Usuario confirma reserva

```
ReservationBar botón "Confirmar"
  ↓
handleConfirm()
  ↓
confirmReservation(1, [seatIds])
  ↓
POST /api/events/1/reservations { seatIds: [3, 5] }
  ↓
Si 200: resync() → refetch full mapa
Si 409/410: mostrar error, mantener estado local
  ↓
SSE notifica a otros: 'seat-reserved'
```

## Componentes

### `Seat.tsx`
```tsx
<Seat 
  seat={{id: 3, status: 'HELD', heldByMe: true, expiresAt: '...'}}
  onHold={(id) => ...}
  onRelease={(id) => ...}
/>
```

Renderiza:
- Status = AVAILABLE: Chip verde (clickeable)
- Status = HELD + heldByMe: Chip naranja (clickeable para liberar)
- Status = HELD: Chip gris (deshabilitado)
- Status = RESERVED: Chip rojo (deshabilitado)

### `SeatMap.tsx`
Agrupa asientos por fila, renderiza rejilla con `<Seat>` componentes.

### `HoldCountdown.tsx`
Progress bar que cuenta hacia atrás desde `expiresAt`. Puramente cosmético (servidor es autoridad).

### `ReservationBar.tsx`
- Barra fija abajo
- Muestra asientos selected (held-by-me)
- Botón "Confirmar Reserva" habilitado solo si ≥1 asiento
- Snackbar para errores (409, 410, etc.)

### `App.tsx`
Orquesta todo:
- Inicializa y sincroniza estado
- Abre EventSource
- Maneja clics en asientos
- Confirma reserva

## Hooks

### `useSeatMap(eventId)`
```ts
const { seats, loading, dispatch, resync } = useSeatMap(1);

// seats: array de Seat
// loading: boolean
// dispatch: reducer para actualizar state
// resync: fn() → refetch mapa desde backend
```

Actions:
- `INIT` → carga inicial
- `SEAT_HELD` → otro cliente o SSE notifica
- `SEAT_RELEASED`
- `SEAT_RESERVED`
- `RESYNC` → reemplaza todo mapa
- `ERROR`

### `useSeatStream(eventId, onSeatHeld, onSeatReleased, onSeatReserved, onResync)`
```ts
useSeatStream(
  1,
  (data) => dispatch({type: 'SEAT_HELD', payload: data, clientId: ...}),
  (data) => dispatch({type: 'SEAT_RELEASED', payload: data}),
  (data) => dispatch({type: 'SEAT_RESERVED', payload: data}),
  resync
);
```

Maneja:
- Abre EventSource
- Escucha eventos por nombre
- Detecta reconexiones (error → open) → dispara onResync
- Limpia on unmount

## API Client (`lib/api.ts`)

```ts
// All funciones envían X-Client-Id header
await getSeatMap(eventId)              // GET /api/events/{eventId}/seats
await holdSeat(eventId, seatId)        // POST /api/events/{eventId}/seats/{seatId}/hold
await holdMultipleSeats(eventId, [...])// POST /api/events/{eventId}/seats/hold
await releaseSeat(eventId, seatId)     // DELETE /api/events/{eventId}/seats/{seatId}/hold
await confirmReservation(eventId, [...])// POST /api/events/{eventId}/reservations
```

Base URL: `VITE_API_URL` variable entorno (default: `/api` con Vite proxy en dev)

## Variables de entorno

`.env`:
```
VITE_API_URL=/api
```

Vite proxy en `vite.config.ts`:
```ts
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  }
}
```

Flujo:
1. Frontend en `http://localhost:5173`
2. Cliente fetch `/api/...` 
3. Vite dev server intercepta
4. Proxea a `http://localhost:8080/api/...`
5. Backend responde

## Tema Material UI (`theme.ts`)

Colores de estado:
- AVAILABLE → success (verde)
- HELD (by me) → warning (naranja)
- HELD (by other) → default (gris)
- RESERVED → error (rojo)

## Tipos (`types.ts`)

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

## Performance

- **Vite**: ~instantáneo HMR para cambios en source
- **TypeScript**: Compilación rápida (tsc -b)
- **Material UI**: Componentes ligeros, CSS-in-JS optimizado
- **EventSource**: Conexión única, low overhead vs polling
- **Virtual threads backend**: Maneja miles de SSE connections

## Troubleshooting

| Problema | Causa | Solución |
|----------|-------|----------|
| 404 /api/... | Backend no corriendo o proxy mal | `make backend` en otra terminal |
| SSE no conecta | CORS o proxy issue | Verificar vite.config.ts, logs del navegador |
| Asiento naranja en ambos navegadores | heldByMe siempre true | Verificar useSeatMap.ts line 29 - debe comparar clientId |
| "X-Client-Id header missing" | API client no envía header | Verificar lib/api.ts headers |
| Timeout en hold | Backend lock timeout | Aumentar `seat.lock.timeout-ms` en backend config |
| Hold no expira visualmente | Timer solo cosmético | Refetch en background o SSE de expiración |

## Build & Deployment

### Development
```bash
bun dev
```

### Production
```bash
bun run build
# Genera dist/
# Deployar dist/ a servidor web (nginx, Vercel, etc.)

# Si backend en misma origin (e.g., /api proxy vía nginx):
# VITE_API_URL=/api (default, no cambio)

# Si backend en dominio diferente:
# VITE_API_URL=https://api.example.com
```

## Browsers soportados

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- Opera 76+

(Cualquier navegador moderno con soporte EventSource)

