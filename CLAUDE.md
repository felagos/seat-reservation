# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Real-time seat reservation system using **pessimistic locking + queue with timeout + double-check** for concurrency safety. Two users cannot reserve the same seat simultaneously.

**Stack:**
- Backend: Java 21, Spring Boot 4.1.0, PostgreSQL, Gradle
- Frontend: React 19, TypeScript, Vite, Material UI, Bun
- Real-time: Server-Sent Events (SSE)
- Infra: Docker multi-stage, docker-compose for dev

## Quick Start Commands

```bash
# Backend + Database (Docker)
make backend

# Backend local (Gradle with hot-reload)
make backend-dev

# Frontend (Vite)
make frontend

# Both together (open 2 terminals)
make backend       # Terminal 1
make frontend      # Terminal 2
# Then open http://localhost:5173
```

Full command reference: `make help`

## Critical Architecture Patterns

### 1. Concurrency: SeatHoldService

Three-layer concurrency control for seat holds:

```
SeatLockRegistry (in-memory)
  ↓ tryLock(timeout) per seat ID
SELECT ... FOR UPDATE (DB pessimistic lock)
  ↓ serializes cross-instance
Double-check (business logic)
  ↓ re-verify status/held_by/held_until after lock acquired
  ↓ handles lazy expiry inline
```

**Key file:** `backend/src/main/java/com/example/demo/service/SeatHoldService.java`

- Public methods call `selfProvider.getObject().doHoldTx()` — **not `this.doHoldTx()`** (AOP proxy requirement)
- `@Transactional` methods must be public; private methods don't get intercepted
- Lock ordering: sort seat IDs ascending before acquiring (deadlock prevention)
- Multi-seat operations must be atomic: acquire all locks → open one transaction → mutate all rows

### 2. Real-time Events: SSE + Domain Events + Redis fanout

`SseBroadcaster` holds `SseEmitter`s **in-memory, per JVM instance** — a live HTTP connection can't be shared across backend replicas. To keep SSE working correctly when the backend is scaled to multiple instances, committed domain events go through Redis Pub/Sub so every instance can fan them out to its own locally-connected clients:

```
@Transactional doHoldTx()
  ↓ inside transaction
  ↓ eventPublisher.publishEvent(SeatHeldEvent)
  ↓ commit
  ↓ @TransactionalEventListener(AFTER_COMMIT)
  ↓ SeatEventListener.onSeatHeld() → publishes SeatEventMessage(eventId, eventName, payload) as JSON
  ↓ redisTemplate.convertAndSend("seat-events", json)
  ↓ Redis Pub/Sub fanout to ALL backend instances
  ↓ each instance: RedisSeatEventSubscriber.onMessage()
  ↓ SseBroadcaster.broadcast() (local to that instance)
  ↓ all EventSource listeners connected to that instance get: {seat-held, seatId, heldBy, expiresAt}
```

Every instance (including the one that published) receives its own message back from Redis and re-broadcasts to its local emitters — a single delivery path regardless of instance count. `SeatEventListener` no longer calls `SseBroadcaster` directly; `SseBroadcaster`, `SseController`, `SseHeartbeatScheduler` are unchanged and still operate purely on local connections, which is correct.

**Why AFTER_COMMIT?** Guarantees SSE only broadcasts committed state. No race between partial transaction and client sync.

**Scaling:** `docker-compose -f docker/docker-compose.dev.yml up --build --scale backend=N`. `nginx` (added in front of `backend`) round-robins across replicas via Docker's embedded DNS (`docker/nginx/nginx.conf`) and is the only service exposing port 8080 to the host — `backend` no longer publishes a host port directly. `SeatLockRegistry` (in-memory `tryLock`) stays a per-instance fail-fast optimization only; cross-instance correctness is still guaranteed by `SELECT ... FOR UPDATE` in `doHoldTx()`/`doConfirmTx()`, unchanged by this.

**Frontend:** `useSeatStream()` hook opens EventSource at `/api/events/{eventId}/stream`, listens for `seat-held` / `seat-released` / `seat-reserved`, dispatches reducer actions. Reconexion triggers full resync (`GET /seats`).

### 3. Configuration & Environment

**Backend:** `application.properties`
- `seat.hold.ttl-seconds=120` — hold auto-expires after 120s
- `seat.lock.timeout-ms=3000` — in-memory tryLock timeout, fail-fast with 409
- `spring.threads.virtual.enabled=true` — cheap virtual threads for SSE connections

**Frontend:** `VITE_API_URL` environment variable
- Dev: `/api` (Vite proxy to `http://localhost:8080`)
- Prod: `http://your-backend-url/api`
- Must use **relative path in dev** to avoid CORS (proxy intercepts `/api/*` requests)

**Why not absolute URL in dev?** Browser sees `http://localhost:8080/api/...`, no proxy interception → CORS block.

### 4. Database Initialization

Ownership moved out of Java entirely. `docker/init-db/init-demo.sql` owns both the **schema** (`CREATE TABLE IF NOT EXISTS`) and the **seed data**:
1. Creates tables `events`, `reservations`, `seats` (+ indexes `idx_event_status`, `idx_held_until`) if missing
2. `TRUNCATE TABLE seats, reservations, events RESTART IDENTITY CASCADE` — wipes rows and resets identity sequences in one statement
3. Inserts demo Event (`id=1`, "Demo Concert") and 50 demo Seats (5 rows A-E × 10 seats each, `status=AVAILABLE`)

Run by the `db-init` service in `docker/docker-compose.dev.yml`, which executes `psql -f init-demo.sql` against Postgres **every time `docker-compose up` runs** (not just on first volume creation — that's why it's a dedicated one-shot service instead of `docker-entrypoint-initdb.d`). Compose ordering: `postgres` (healthy) → `db-init` (runs, exits 0) → `backend` (starts).

Because schema ownership lives in SQL now, backend no longer creates/alters tables — `spring.jpa.hibernate.ddl-auto=validate` (was `update`). Local `bootRun`/`backend-dev` flow needs `db-init` to have run at least once against the target Postgres first: `docker-compose -f docker/docker-compose.dev.yml up postgres db-init` before `./gradlew bootRun`.

Frontend: `useSeatMap(eventId)` calls `GET /api/events/{eventId}/seats` on mount (eventId=1 in demo) and gets the freshly-seeded data. There is no `/api/admin/init-demo` endpoint anymore — `AdminController`/`AdminService`/`DatabaseInitializer` were deleted; reseeding only happens via `docker-compose up`, not HTTP.

### 5. Hold Expiration

Two mechanisms:

1. **Lazy expiry:** In `SeatHoldService.doHoldTx()` double-check, if `held_until < now`, treat as available immediately
2. **Sweep job:** `SeatSweepService` runs every 15s (`seat.sweep.interval-ms`), locks expired seats and reverts to AVAILABLE

Both use the same lock→FOR UPDATE→double-check path to ensure safety.

### 6. Frontend State Management

`useSeatMap()` reducer combines:
- Initial fetch: `GET /api/events/{eventId}/seats` → `INIT` action
- SSE deltas: `SEAT_HELD`, `SEAT_RELEASED`, `SEAT_RESERVED` actions
- Manual resync: `RESYNC` action (refetch full map)

**Critical:** When `SEAT_HELD` event arrives, compare `heldBy` field against `clientId` to set `heldByMe` boolean. If not yours, UI shows "held by other" (disabled, gray). If yours, UI shows "held by me" (enabled, orange).

## Common Pitfalls

### @Transactional on private method
❌ Does not work — Spring proxy cannot intercept private methods.

✅ Use `ObjectProvider<SeatHoldService> selfProvider` in constructor, then call `selfProvider.getObject().doHoldTx()` inside public method.

### CORS errors in dev
❌ `VITE_API_URL=http://localhost:8080/api` in frontend → browser sees absolute URL → CORS block.

✅ Use `VITE_API_URL=/api` → Vite proxy intercepts and forwards to backend.

### SSE not staying alive
Backend `SseHeartbeatScheduler` sends heartbeats every 15s to prevent proxy timeouts. If you add new features that block the event loop, SSE may appear stuck.

### Concurrency race on multi-seat hold
If seat IDs not sorted before acquiring locks, two threads holding {3,5} and {5,3} can deadlock waiting for each other.

✅ Always sort IDs ascending: `List<Long> sorted = seatIds.stream().sorted().collect(...)` before passing to `lockRegistry.withLocks()`.

## Testing Notes

No automated tests currently (marked as "future"). For manual testing:
- Open `http://localhost:5173` in two browser tabs
- Click same seat nearly simultaneously — only one succeeds
- Hold seats, wait 120s or manual refresh to see expiry
- Verify SSE updates across tabs (seat colors sync)

## Key Files to Understand First

1. **Backend concurrency & initialization:**
   - `docker/init-db/init-demo.sql` — owns schema + truncates DB + loads demo data, run by `db-init` compose service
   - `backend/src/main/java/com/example/demo/service/SeatHoldService.java` — core hold/confirm/release logic
   - `backend/src/main/java/com/example/demo/service/SeatLockRegistry.java` — in-memory lock queue
   - `backend/src/main/java/com/example/demo/sse/SeatEventListener.java` — event→SSE bridge

2. **Frontend sync:**
   - `frontend/src/hooks/useSeatMap.ts` — reducer for seat state
   - `frontend/src/hooks/useSeatStream.ts` — EventSource + reconnect logic
   - `frontend/src/App.tsx` — orchestrates both hooks

3. **Data model:**
   - `backend/src/main/java/com/example/demo/domain/Seat.java` — status, held_by, held_until fields
   - `backend/src/main/java/com/example/demo/repository/SeatRepository.java` — pessimistic lock queries

## Build & Compilation

```bash
# Backend
cd backend
./gradlew build -x test      # Compile without tests (includes -parameters flag)
./gradlew clean              # Clean artifacts
docker-compose -f docker/docker-compose.dev.yml up --build -d  # Rebuild Docker image

# Frontend
cd frontend
bun install                  # Install deps (use Bun, not npm)
bun run build                # Production build
bun run lint                 # Run oxlint (fast linter)
```

Vite dev server includes TypeScript type-checking and hot reload via `bun dev`.

## Compiler Flags & Logging

**Java Compiler** (`build.gradle.kts`):
- `-parameters` flag enabled: Spring @PathVariable/@RequestParam parameter name resolution without reflection
- Without it: "Name for argument not specified" error at runtime on @PathVariable Long seatId, etc.

**Logging** (SLF4J + Log4j2):
- No Logback (excluded from spring-boot-starter-logging)
- Database init logs now come from the `db-init` container's `psql` output, not Java/Log4j2

## Debugging Tips

**Backend:** Enable SQL logging in `application.properties`:
```
logging.level.org.hibernate.SQL=DEBUG
```

**Frontend:** Check Vite proxy in DevTools Network tab — requests to `/api/...` should show as proxied to `localhost:8080`.

**SSE:** Use browser DevTools (Application > EventSource) to watch incoming messages in real-time.

## References

- `README.md` — full system overview + diagrams
- `backend/README.md` — API endpoints, database schema
- `frontend/README.md` — component architecture, hooks
- `Makefile` — all build/dev commands
