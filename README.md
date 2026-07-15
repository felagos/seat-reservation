# Real-time Seat Reservation System

Classic seat reservation system with real-time updates using **Server-Sent Events (SSE)**, implemented with **pessimistic locking**, **queue with timeout**, and **double-check** to ensure two users cannot reserve the same seat simultaneously.

## Key Features

- ✅ **Real-time updates**: SSE for instant synchronization across browsers
- ✅ **Safe concurrency**: Pessimistic locking + queue with timeout + double-check
- ✅ **Temporary hold**: Seats can be selected (hold) and automatically expire
- ✅ **Multi-seat**: Reserve multiple seats in one atomic transaction
- ✅ **Responsive UI**: Material UI with status-based colors
- ✅ **Local development with hot-reload**: Vite for frontend, Gradle for backend
- ✅ **Docker**: Multi-stage image for production, compose for development

## Tech Stack

### Backend
- **Java 21** with Spring Boot 4.1.0
- **PostgreSQL 16** for persistence
- **JPA/Hibernate** with pessimistic locking (`SELECT ... FOR UPDATE`)
- **SSE** via Spring MVC `SseEmitter`
- **Redis Pub/Sub** for SSE event fanout across multiple backend instances
- **Virtual threads** for efficient connection handling
- **Gradle 8.14** as build system
- **nginx** as round-robin load balancer for scaled backend

### Frontend
- **React 19** + TypeScript
- **Vite 8** as bundler
- **Material UI 6** for components
- **TanStack Query** for server state management
- **Zustand** for UI state
- **CSS Modules** for component styling
- **Bun** as package manager
- **EventSource (SSE)** for real-time connection

## Getting Started

### Option 1: Docker (recommended - no local dependencies)
```bash
# Terminal 1: PostgreSQL + Backend in Docker
make backend

# Terminal 2: Frontend (Vite)
make frontend

# Open http://localhost:5173
```

### Option 2: Local development with hot-reload
```bash
# Terminal 1: PostgreSQL in Docker + Backend in Gradle (hot-reload)
make backend-dev

# Terminal 2: Frontend (Vite)
make frontend

# Open http://localhost:5173
```

### Manual
```bash
# Option 1 - Docker:
docker-compose -f docker/docker-compose.dev.yml up

# Option 2 - Local:
# Terminal 1: docker-compose -f docker/docker-compose.dev.yml up mariadb db-init
# Terminal 2: cd backend && ./gradlew bootRun
# Terminal 3: cd frontend && bun dev
```

Schema and demo data (event id=1 + 50 seats) are no longer created by the backend: they are owned by `docker/init-db/init-demo.sql`, executed by the `db-init` service every time `docker-compose up` runs (order: `mariadb` → `db-init` → `backend`). Backend uses `ddl-auto=validate`, so it requires `db-init` to have run at least once against the MariaDB it's using.

### Scale to multiple backend instances

```bash
docker-compose -f docker/docker-compose.dev.yml up --build --scale backend=3
```

`backend` no longer exposes a direct port to the host — `nginx` (port 8080) does round-robin load balancing across replicas. SSE events are synchronized between instances via Redis Pub/Sub, so a hold processed by one replica notifies SSE clients connected to another replica. See `backend/README.md` section "Performance & Scalability" for details.

## Makefile commands

| Command | Description |
|---------|-------------|
| `make backend` | PostgreSQL + Backend in Docker (recommended) |
| `make backend-dev` | PostgreSQL (docker) + Backend (gradle with hot-reload) |
| `make frontend` | Vite dev server |
| `make db-up` | Start PostgreSQL in Docker only |
| `make db-down` | Stop PostgreSQL + Backend |
| `make build-backend` | Build multi-stage Docker image |
| `make dev` | Alias for `backend-dev` |
| `make all` | Show startup instructions |
| `make clean` | Clean build artifacts |
| `make help` | Show help |

## Ports

- **Backend** (via nginx): `http://localhost:8080/api`
- **Frontend**: `http://localhost:5173`
- **PostgreSQL**: `localhost:5432`
- **Redis**: internal to Docker network (not exposed to host)

## Project Structure

```
seat-reservation/
├── backend/                    # Spring Boot application
│   ├── src/main/java/
│   │   └── com/example/demo/
│   │       ├── domain/         # Entities (Seat, Reservation)
│   │       ├── repository/     # JPA repositories
│   │       ├── service/        # Business logic (SeatHoldService, SeatLockRegistry)
│   │       ├── web/            # REST controllers
│   │       ├── sse/            # SSE handlers + Redis fanout (SeatEventListener, RedisSeatEventSubscriber)
│   │       ├── config/         # RedisConfig (Pub/Sub topic + listener container)
│   │       ├── event/          # Domain events
│   │       └── exception/      # Custom exceptions
│   ├── Dockerfile              # Multi-stage build
│   └── build.gradle.kts
│
├── frontend/                   # React + Vite application
│   ├── src/
│   │   ├── components/         # React components (SeatMap, Seat, etc.) - one folder per component
│   │   ├── hooks/              # Custom hooks (useSeatsQuery, useSeatStream, mutations)
│   │   ├── lib/                # Utilities (API client, clientId, queryClient, queryKeys)
│   │   ├── store/              # Zustand state store (useUIStore)
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── vite.config.ts
│   └── package.json
│
├── docker/
│   ├── docker-compose.dev.yml  # PostgreSQL + db-init + Redis + Backend (scalable) + nginx
│   ├── init-db/
│   │   └── init-demo.sql       # Schema (CREATE TABLE) + seed demo data
│   └── nginx/
│       └── nginx.conf          # Round-robin load balancer for backend replicas
│
└── Makefile                    # Build automation

```

## Use Cases

### Case 0: Initialization and seat map loading

```
docker-compose up          db-init container           Database              User opens app         Frontend
────────────────────────────────────────────────────────────────────────────────────────────────────────────
     │
     ├─ mariadb healthy
     ├─ db-init: mariadb < init-demo.sql
     │                            │
     │                            ├─ CREATE TABLE IF NOT EXISTS reservations/seats
     │                            ├─ TRUNCATE (FK checks off) each table
     │
     │                            ├─ CREATE 50 Seats (5 rows x 10)
     │                            │  INSERT INTO seats (all status=AVAILABLE)
     │
     │                            ├─ COMMIT
     │
     ├─ db-init exits 0 ──────────◄───────── Initialization complete
     ├─ backend starts (ddl-auto=validate)
     │
                                                    User accesses http://localhost:5173
                                                           │
                                                           ├─ App.tsx mounts
                                                           │
                                                           ├─ useSeatsQuery()
                                                           │  useEffect → GET /api/seats
     │                                                                          │
     │────────────────────────────▶ SELECT * FROM seats
     │
                                                           ◄────────────────── 200 OK [
                                                                {id:1, row:A, seat:1, status:AVAILABLE},
                                                                {id:2, row:A, seat:2, status:AVAILABLE},
                                                                ...,
                                                                {id:50, row:E, seat:10, status:AVAILABLE}
                                                              ]
                                                           │
                                                           ├─ setQueryData with response
                                                           │
                                                           ├─ useSeatStream()
                                                           │  new EventSource('/api/seats/stream')
     │                                                                          │
     │◄──────────────────────────────── GET /api/seats/stream (text/event-stream)
     │
                                                           ├─ UI renders grid (5 rows x 10 seats)
                                                           │  All seats = GREEN (AVAILABLE, clickable)
                                                           │
                                                           └─ EventSource listening:
                                                              addEventListener('seat-held', ...)
                                                              addEventListener('seat-reserved', ...)
                                                              addEventListener('seat-released', ...)
```

**Result**: Backend truncates DB and initializes demo on startup. User opens app and sees clean map with 50 available seats. EventSource ready for real-time changes.

---

### Case 1: One user selects a seat

```
User A (browser 1)               Backend                Database
─────────────────────────────────────────────────────────────────
     │
     ├─ Click Seat 3 ────────────▶ POST /api/.../seats/3/hold
     │                             │
     │                             ├─ SeatLockRegistry.tryLock(3000ms)
     │                             │  ✓ Lock acquired
     │                             │
     │                             ├─ @Transactional doHoldTx()
     │                             │  ├─ SELECT ... FOR UPDATE ───────▶ Row 3 locked
     │                             │  │
     │                             │  Double-check:
     │                             │  ├─ Status = AVAILABLE ✓
     │                             │  ├─ Update state ────────────────▶ status=HELD
     │                             │  │                                 held_by=UUID-A
     │                             │  │                                 held_until=now+120s
     │                             │  └─ Event: SeatHeldEvent(...)
     │                             │
     │                             ├─ Commit transaction ──────────────▶ Row 3 unlocked
     │                             │  ├─ EventListener @AFTER_COMMIT
     │                             │  └─ Redis publish("seat-events") → each instance: SseBroadcaster.broadcast() local
     │                             │
     │                    ◄────────┤ SSE: {seat-held, seatId:3, ...}
     │                             │
     ◄────────────────────────────── 200 OK {expiresAt: "2026-07-11T02:20:00Z"}
     │
     ├─ UI: Seat 3 orange (held-by-me)
     │
     └─ HoldCountdown: 120s ⏱️  ⏱️  ⏱️

User B (browser 2)               Backend                SSE stream
─────────────────────────────────────────────────────────────────
     │                             │
     │ EventSource connected ◄─────┤ SSE: {seat-held, seatId:3, heldBy:UUID-A}
     │                             │
     ├─ UI: Seat 3 gray (held-by-other)
     │
```

**Result**: Seat 3 appears orange (A) and gray (B) in real-time.

---

### Case 2: Two users try to select the same seat simultaneously

```
User A (UUID-A)                  User B (UUID-B)          Backend
─────────────────────────────────────────────────────────────────
     │                                │
     ├─ Click Seat 7 ─────────────────▶ Click Seat 7
     │  (t=0ms)                       │  (t=1ms)
     │                                │
     ├─ holdSeat(7) ────────────────────────────────▶ Memory lock: tryLock(7)
     │                                               ✓ A acquires lock
     │
     ├─ holdSeat(7) ──────────────────────────────▶ Memory lock: tryLock(7)
     │                                             ✗ B waits (blocked)
     │
     ├─ POST /seats/7/hold ────────────────────────▶ SELECT FOR UPDATE (7)
     │                                             ✓ A has DB lock
     │
     ├─ Double-check: AVAILABLE ✓ ─────────────────▶ status = HELD
     │                                             │ held_by = UUID-A
     │                                             │ Commit
     │                                             │
     │                                             ├─ Release lock (7) DB
     │                                             │
     │                                             └─ Release lock (7) memory
     │                                                B: tryLock(7) ✓ acquires
     │
     ◄───────────────────────────────────────────── 200 OK
     │ Seat 7 = ORANGE (held-by-me)
     │
     │                                │ B: POST /seats/7/hold
     │                                │  ├─ SELECT FOR UPDATE (7)
     │                                │  ├─ Double-check: HELD (by A) ✗
     │                                │  └─ throw SeatUnavailableException
     │                                │
     │                                ◄────────── 409 Conflict
     │                                │ Error: "Seat not available"
     │                                │
```

**Result**: Only A gets the seat. B gets 409 (no double reservation).

---

### Case 3: User confirms reservation

```
User A (hold 3 seats)            Backend                Database
─────────────────────────────────────────────────────────────────
     │
     ├─ [3, 5, 7] Seats orange
     │  (all held-by-me)
     │
     ├─ Click "Confirm Reservation" ──▶ POST /api/.../reservations
     │                               │
     │                               ├─ SeatLockRegistry.tryLock([3,5,7])
     │                               │  ✓ All locks acquired (order: 3,5,7)
     │                               │
     │                               ├─ @Transactional doConfirmTx()
     │                               │  ├─ SELECT FOR UPDATE WHERE id IN (3,5,7)
     │                               │  │  ─────────────────────────────────────▶ Rows locked
     │                               │  │
     │                               │  ├─ Double-check each one:
     │                               │  │  ├─ Seat 3: HELD + held_by=UUID-A + not expired ✓
     │                               │  │  ├─ Seat 5: HELD + held_by=UUID-A + not expired ✓
     │                               │  │  └─ Seat 7: HELD + held_by=UUID-A + not expired ✓
     │                               │  │
     │                               │  ├─ Create Reservation(id=123)
     │                               │  └─ For each seat:
     │                               │     └─ status = RESERVED, reservation_id = 123
     │                               │
     │                               ├─ Commit ────────────────────────────────▶ Rows unlocked
     │                               │  ├─ EventListener @AFTER_COMMIT
     │                               │  ├─ SeatReservedEvent(3), SeatReservedEvent(5), ...
     │                               │  └─ Redis publish("seat-events") → each instance: SseBroadcaster.broadcast() local
     │                               │
     │                    ◄──────────┤ SSE to ALL: {seat-reserved, seatId:3/5/7}
     │
     ◄────────────────────────────── 200 OK {reservationId: 123}
     │
     ├─ UI: Seats 3, 5, 7 = RED (reserved)
     │       "Confirm" button disabled
     │       "Reservation confirmed" shown
     │

User C (another browser)            Backend                SSE stream
─────────────────────────────────────────────────────────────────
     │                             │
     │ EventSource connected ◄─────┤ SSE: {seat-reserved, seatId:3}
     │                             │       {seat-reserved, seatId:5}
     │                             │       {seat-reserved, seatId:7}
     │
     ├─ UI: Seats 3, 5, 7 = RED (reserved)
     │       Cannot select
```

**Result**: Reservation confirmed atomically. All see red.

---

### Case 4: Hold expires automatically (after 120s)

```
User A (hold Seat 9)             Backend Sweep Job (every 15s)    Database
────────────────────────────────────────────────────────────────────────────
     │
     ├─ t=0s: Click Seat 9
     │        status = HELD
     │        held_until = t+120s
     │
     ├─ t=15s: UI shows "Expires in: 105s" ⏱️
     │
     ├─ t=30s: UI shows "Expires in: 90s" ⏱️
     │
     ├─ t=120s: UI shows "Expires in: 0s" ⏱️
     │
     │                                                  │
     │                   t=120s: Sweep job runs ───────▶ SELECT * FROM seats
     │                           WHERE status='HELD'
     │                           AND held_until < now
     │                                                  │
     │                           ├─ SeatLockRegistry.tryLock([9])
     │                           │
     │                           ├─ @Transactional doExpireTx()
     │                           │  ├─ SELECT FOR UPDATE WHERE id=9
     │                           │  │  ───────────────────────────────▶ Row locked
     │                           │  │
     │                           │  ├─ Double-check:
     │                           │  │  ├─ status = HELD ✓
     │                           │  │  └─ held_until < now ✓
     │                           │  │
     │                           │  └─ Update: status = AVAILABLE
     │                           │     held_by = NULL
     │                           │     held_until = NULL
     │                           │  ───────────────────────────────▶ Row updated
     │                           │
     │                           ├─ Commit
     │                           │  ├─ EventListener @AFTER_COMMIT
     │                           │  └─ SeatReleasedEvent(9)
     │                           │
     │                ◄──────────┤ SSE to ALL: {seat-released, seatId:9}
     │
     ├─ UI: Seat 9 becomes GREEN (available)
     │       Counter disappears
     │       Can select again
     │

User B (another browser)          Backend                SSE stream
────────────────────────────────────────────────────────────────────
     │ EventSource connected ◄────┤ SSE: {seat-released, seatId:9}
     │
     ├─ UI: Seat 9 becomes GREEN
     │       Can select
```

**Result**: Hold expires after 120s. Seat becomes available to all again.

---

## Concurrency Flow

### 1. Select a seat (hold)
```
Client → POST /api/events/1/seats/3/hold
         ↓
SeatLockRegistry (in-memory lock with timeout)
         ↓
DB Transaction: SELECT ... FOR UPDATE (pessimistic lock)
         ↓
Double-check: status == AVAILABLE? held_until expired?
         ↓
Update: status = HELD, held_by = clientId, held_until = now + 120s
         ↓
Publish event → SSE → All browsers updated
```

### 2. Confirm reservation (confirm)
```
Client with held-by-me seats → POST /api/events/1/reservations
         ↓
SeatLockRegistry + SELECT ... FOR UPDATE (same sequence)
         ↓
Double-check: All held by this clientId? Still valid?
         ↓
Create Reservation, update status = RESERVED
         ↓
SSE Event → All see seat RESERVED
```

### 3. Automatic expiration (sweep)
```
Periodic job (every 15s) → Find HELD seats with held_until < now
         ↓
For each seat: same lock + SELECT ... FOR UPDATE
         ↓
Double-check: Still expired?
         ↓
Revert: status = AVAILABLE, held_by = null
         ↓
SSE Event → All see seat available again
```

## Frontend: SSE Synchronization

```
1. EventSource opened → /api/seats/stream
2. Listens for events: seat-held, seat-released, seat-reserved
3. Updates React Query cache via setQueryData
4. Auto-reconnect: full map refetch on reconnect
5. UI reflects changes instantly
```

## User Experience

1. Open `http://localhost:5173` in browser
2. Seat map loads automatically (seed from `docker/init-db/init-demo.sql`)
3. Click a seat → Turns **orange** (held-by-me)
4. Seat expires after 120 seconds → Turns gray (available)
5. Multiple seats → Click "Confirm Reservation" button
6. Seat turns **red** (reserved) and appears in other browsers

## API Endpoints

Single global seat pool (no event concept — POC scope).

### Seats
- `GET /api/seats` - Full seat map
- `POST /api/seats/{seatId}/hold` - Select 1 seat
- `POST /api/seats/hold` - Select N seats
- `DELETE /api/seats/{seatId}/hold` - Release seat

### Reservations
- `POST /api/reservations` - Confirm reservation (multi-seat)

### SSE
- `GET /api/seats/stream` - EventSource stream
  - Events: `seat-held`, `seat-released`, `seat-reserved`

No HTTP admin/reinit endpoint. Seed (schema + truncate + demo data) runs via `docker/init-db/init-demo.sql`, executed by `db-init` service each `docker-compose up` run (see "Getting Started" section).

## Development

### Backend
See [backend/README.md](backend/README.md)

### Frontend
See [frontend/README.md](frontend/README.md)

## Design Notes

- `X-Client-Id`: UUID generated by client (simplification, not for production)
- **Resync on reconnect**: Full refetch (simple and always correct)
- **Sweep**: Lazy expiration on double-check + periodic job (15s)
- **Virtual threads**: `spring.threads.virtual.enabled=true` for scalability
- **Multi-stage Docker**: Reduces image size ~30-40%

## Scalability

- `SeatLockRegistry` is per JVM → multiple instances: DB lock guarantees correctness, but timeout fail-fast is lost
- For production: Flyway/Liquibase, Spring Security, Redis for cross-instance events
