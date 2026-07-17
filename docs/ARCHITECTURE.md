# Critical Architecture Patterns

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
  ↓ SeatEventListener.onSeatHeld() → publishes SeatEventMessage(eventName, payload) as JSON
  ↓ redisTemplate.convertAndSend("seat-events", json)
  ↓ Redis Pub/Sub fanout to ALL backend instances
  ↓ each instance: RedisSeatEventSubscriber.onMessage()
  ↓ SseBroadcaster.broadcast() (local to that instance)
  ↓ all EventSource listeners connected to that instance get: {seat-held, seatId, heldBy, expiresAt}
```

Every instance (including the one that published) receives its own message back from Redis and re-broadcasts to its local emitters — a single delivery path regardless of instance count. `SeatEventListener` no longer calls `SseBroadcaster` directly; `SseBroadcaster`, `SseController`, `SseHeartbeatScheduler` are unchanged and still operate purely on local connections, which is correct.

**Why AFTER_COMMIT?** Guarantees SSE only broadcasts committed state. No race between partial transaction and client sync.

**Scaling:** `docker-compose -f docker/docker-compose.dev.yml up --build --scale backend=N`. `nginx` (added in front of `backend`) round-robins across replicas via Docker's embedded DNS (`docker/nginx/nginx.conf`) and is the only service exposing port 8080 to the host — `backend` no longer publishes a host port directly. `SeatLockRegistry` (in-memory `tryLock`) stays a per-instance fail-fast optimization only; cross-instance correctness is still guaranteed by `SELECT ... FOR UPDATE` in `doHoldTx()`/`doConfirmTx()`, unchanged by this.

**Frontend:** `useSeatStream()` hook opens EventSource at `/api/seats/stream`, listens for `seat-held` / `seat-released` / `seat-reserved`, dispatches reducer actions. Reconexion triggers full resync (`GET /api/seats`).

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
1. Creates tables `reservations`, `seats` (+ indexes `idx_status`, `idx_held_until`) if missing
2. `SET FOREIGN_KEY_CHECKS=0; TRUNCATE` each table, then re-enables checks — wipes rows and resets `AUTO_INCREMENT` counters
3. Inserts 50 demo Seats (5 rows A-E × 10 seats each, `status=AVAILABLE`)

Run by the `db-init` service in `docker/docker-compose.dev.yml`, which pipes `init-demo.sql` into the `mariadb` client against MariaDB **every time `docker-compose up` runs** (not just on first volume creation — that's why it's a dedicated one-shot service instead of an entrypoint-initdb script). Compose ordering: `mariadb` (healthy) → `db-init` (runs, exits 0) → `backend` (starts).

Because schema ownership lives in SQL now, backend no longer creates/alters tables — `spring.jpa.hibernate.ddl-auto=validate` (was `update`). Local `bootRun`/`backend-dev` flow needs `db-init` to have run at least once against the target MariaDB first: `docker-compose -f docker/docker-compose.dev.yml up mariadb db-init` before `./gradlew bootRun`.

Frontend: `useSeatsQuery()` calls `GET /api/seats` on mount and gets the freshly-seeded data. There is no `/api/admin/init-demo` endpoint anymore — `AdminController`/`AdminService`/`DatabaseInitializer` were deleted; reseeding only happens via `docker-compose up`, not HTTP.

**No event concept:** this is a single-pool POC — one flat list of seats, no `Event` entity, no `event_id` FK, no `{eventId}` in any URL. All endpoints live under `/api/seats` and `/api/reservations` directly.

### 5. Hold Expiration

Two mechanisms:

1. **Lazy expiry:** In `SeatHoldService.doHoldTx()` double-check, if `held_until < now`, treat as available immediately
2. **Sweep job:** `SeatSweepService` runs every 15s (`seat.sweep.interval-ms`), locks expired seats and reverts to AVAILABLE

Both use the same lock→FOR UPDATE→double-check path to ensure safety.

### 6. Frontend State Management

`useSeatMap()` reducer combines:
- Initial fetch: `GET /api/seats` → `INIT` action
- SSE deltas: `SEAT_HELD`, `SEAT_RELEASED`, `SEAT_RESERVED` actions
- Manual resync: `RESYNC` action (refetch full map)

**Critical:** When `SEAT_HELD` event arrives, compare `heldBy` field against `clientId` to set `heldByMe` boolean. If not yours, UI shows "held by other" (disabled, gray). If yours, UI shows "held by me" (enabled, orange).
