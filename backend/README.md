# Backend - Seat Reservation System

Spring Boot 4.1.0 with Java 21 - REST API + SSE for real-time seat reservation.

## Architecture

### Layers

```
web/                    → REST controllers + DTOs
  ├── SeatMapController
  ├── SeatHoldController
  └── ReservationController

service/                → Business logic
  ├── SeatHoldService (core)
  ├── SeatLockRegistry (concurrency)
  └── SeatSweepService (expiration)

domain/                 → JPA entities
  ├── Event
  ├── Seat
  ├── Reservation
  ├── SeatStatus enum
  └── ReservationStatus enum

repository/             → JPA repositories
  ├── EventRepository
  ├── SeatRepository (with pessimistic locking)
  └── ReservationRepository

sse/                    → Server-Sent Events + Redis fanout
  ├── SseBroadcaster
  ├── SseController
  ├── SseHeartbeatScheduler
  ├── SeatEventListener (publishes to Redis)
  ├── RedisSeatEventSubscriber (receives from Redis, rebroadcasts local)
  └── SeatEventMessage (envelope eventId/eventName/payload)

config/
  └── RedisConfig (ChannelTopic + RedisMessageListenerContainer)

event/                  → Domain events
  ├── SeatHeldEvent
  ├── SeatReleasedEvent
  └── SeatReservedEvent

exception/              → Custom exceptions
  ├── SeatLockTimeoutException
  ├── SeatUnavailableException
  ├── SeatNotOwnedException
  └── HoldExpiredException
```

## Concurrency: Pessimistic Locking + Queue + Double-Check

### Flow: Select seat (hold)

1. **In-memory lock** (`SeatLockRegistry`):
   - `ConcurrentHashMap<Long, ReentrantLock>` per `seatId`
   - `tryLock(timeout)` → fail-fast with 409 on timeout

2. **DB pessimistic lock**:
   - `@Lock(LockModeType.PESSIMISTIC_WRITE)` in JPA
   - `SELECT ... FOR UPDATE` in MariaDB
   - Serializes across multiple backend instances

3. **Double-check**:
   - Re-verifies `status`, `heldBy`, `heldUntil` immediately after read
   - Handles lazy-expiry here: if `held_until < now`, treat as AVAILABLE
   - Throws `SeatUnavailableException` if not available

4. **Transaction**:
   - Mutate: status = HELD, held_by = clientId, held_until = now + TTL
   - Publish domain event
   - Commit releases DB lock

5. **SSE after-commit (multi-instance fanout via Redis)**:
   - `SeatEventListener` with `@TransactionalEventListener(AFTER_COMMIT)`
   - Guarantees SSE only reflects committed state
   - Publishes `SeatEventMessage` as JSON to Redis channel `seat-events` (does not call `SseBroadcaster` directly)
   - **All** backend instances are subscribed to that channel (`RedisConfig` + `RedisSeatEventSubscriber`)
   - Each instance receives the message and calls its own `SseBroadcaster.broadcast()` locally, delivering to its connected clients
   - Thus, a hold processed on instance 2 notifies a client connected by SSE to instance 1 equally

### Key Code

```java
// In SeatHoldService.hold()
List<Long> sorted = seatIds.stream().sorted().collect(Collectors.toList());
return lockRegistry.withLocks(sorted, lockTimeoutMs, () ->
    selfProvider.getObject().doHoldTx(eventId, sorted, clientId)
);

// In doHoldTx()
@Transactional
public List<SeatHoldResponse> doHoldTx(Long eventId, List<Long> seatIds, String clientId) {
    List<Seat> seats = seatRepository.findAllByIdForUpdate(seatIds); // SELECT FOR UPDATE
    
    for (Seat seat : seats) {
        // Double-check
        if (seat.getStatus() == SeatStatus.AVAILABLE) {
            seat.setStatus(SeatStatus.HELD);
            seat.setHeldBy(clientId);
            seat.setHeldUntil(expiresAt);
            seatRepository.save(seat);
            eventPublisher.publishEvent(new SeatHeldEvent(...));
        } else if (lazy_expire_check) {
            // ... treat as available
        } else {
            throw new SeatUnavailableException(seat.getId());
        }
    }
    return responses;
}
```

## Endpoints

Dynamic per event. Demo uses **eventId=1**.

### Seats

**GET** `/api/events/{eventId}/seats`
```
Returns: List<SeatDto> sorted by rowLabel asc, seatNumber asc
Headers: X-Client-Id (optional, to mark held-by-me)
Response: 200 OK
```

**POST** `/api/events/{eventId}/seats/{seatId}/hold`
```
Select 1 seat
Headers: X-Client-Id (required)
Response: 200 {seatId, rowLabel, seatNumber, expiresAt}
         409 Conflict (unavailable, timeout, etc.)
```

**POST** `/api/events/{eventId}/seats/hold`
```
Select N seats
Headers: X-Client-Id
Body: {seatIds: [1, 2, 3]}
Response: 200 List<{seatId, rowLabel, seatNumber, expiresAt}>
         409 Conflict (one unavailable)
```

**DELETE** `/api/events/{eventId}/seats/{seatId}/hold`
```
Release a seat (only owner can)
Headers: X-Client-Id
Response: 204 No Content
         403 Forbidden (not owner)
         404 Not Found
```

### Reservations

**POST** `/api/events/{eventId}/reservations`
```
Confirm multi-seat reservation
Headers: X-Client-Id
Body: {seatIds: [1, 2, 3]}
Response: 200 {id, holderId, status}
         409 Conflict (not all held by this client)
         410 Gone (hold expired)
```

### SSE

**GET** `/api/events/{eventId}/stream`
```
EventSource stream (text/event-stream)
Events:
  - seat-held: {seatId, heldBy, expiresAt}
  - seat-released: {seatId}
  - seat-reserved: {seatId, reservationId}
Kept alive with heartbeat every 15s
```

### Health Checks

**GET** `/actuator/health`
```
Spring Boot Actuator health check endpoint
Response: 200 {status, components: {db, redis}}
Shows status of database and Redis connections
Details always shown (configured in application.properties)
```

## Configuration

`application.properties`:
```properties
spring.datasource.url=jdbc:mariadb://localhost:3306/seatres
spring.datasource.username=root
spring.datasource.password=root

spring.jpa.hibernate.ddl-auto=validate
spring.threads.virtual.enabled=true

# Redis (SSE event fanout between instances)
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Timeouts and intervals
seat.hold.ttl-seconds=120
seat.lock.timeout-ms=3000
seat.sweep.interval-ms=15000

# Actuator Health Checks
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=always
management.health.redis.enabled=true
management.health.db.enabled=true
```

## Initialization (docker/init-db/init-demo.sql + db-init service)

Schema and seed no longer live in Java. `docker/init-db/init-demo.sql` owns both:
1. **Schema**: `CREATE TABLE IF NOT EXISTS` for events, reservations, seats + indices `idx_event_status`, `idx_held_until`
2. **Truncate + reset**: `SET FOREIGN_KEY_CHECKS=0; TRUNCATE` each table, then re-enables checks — resets `AUTO_INCREMENT` too
3. **Seed**: creates Event id=1 + 50 Seats (A1-E10)

Executed by `db-init` service in `docker/docker-compose.dev.yml` (pipes `init-demo.sql` into the `mariadb` client), which runs **every time `docker-compose up` is run** (not just the first time the volume is created). Startup order: `mariadb` (healthy) → `db-init` (runs and exits) → `backend` (starts).

Since SQL owns the schema, backend no longer creates/alters tables: `spring.jpa.hibernate.ddl-auto=validate`. To run backend locally (`./gradlew bootRun` / `make backend-dev`) against a MariaDB that doesn't yet have the schema, first:
```bash
docker-compose -f ../docker/docker-compose.dev.yml up mariadb db-init
```

## Logging

**Stack**: SLF4J + Log4j2 (not Logback)

**Build**: `-parameters` flag in compiler so Spring resolves @PathVariable/@RequestParam without explicit names

**Levels by package** (configurable in `log4j2.xml` if added):
- `com.seatreservation.service` → DEBUG (SeatHoldService)
- `com.seatreservation.repository` → WARN
- `org.springframework` → INFO

## Database

### Schema (created by docker/init-db/init-demo.sql, see Initialization section)

```sql
-- Events
CREATE TABLE events (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  starts_at TIMESTAMP NOT NULL
);

-- Seats
CREATE TABLE seats (
  id BIGSERIAL PRIMARY KEY,
  event_id BIGINT NOT NULL REFERENCES events,
  row_label VARCHAR(10) NOT NULL,
  seat_number VARCHAR(10) NOT NULL,
  status VARCHAR(20) NOT NULL, -- AVAILABLE, HELD, RESERVED
  held_by VARCHAR(255),        -- client UUID
  held_until TIMESTAMP,        -- NULL if not held
  reservation_id BIGINT REFERENCES reservations,
  INDEX(event_id, status),
  INDEX(held_until)
);

-- Reservations
CREATE TABLE reservations (
  id BIGSERIAL PRIMARY KEY,
  event_id BIGINT NOT NULL REFERENCES events,
  holder_id VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL, -- CONFIRMED
  created_at TIMESTAMP NOT NULL
);
```

## Local Development

### Prerequisites
- Java 21+
- Gradle 8.14+ (or use bundled `./gradlew`)
- PostgreSQL 16+ (or Docker)

### Build

```bash
cd backend
./gradlew build -x test    # Compile without tests
./gradlew clean           # Clean artifacts
```

### Run

```bash
# With MariaDB + schema/seed + Redis in Docker
docker-compose -f ../docker/docker-compose.dev.yml up mariadb db-init redis

# Run backend
./gradlew bootRun
# Listens on http://localhost:8080/api
```

### Test (future)
```bash
./gradlew test
```

## Docker

### Multi-stage Build

```dockerfile
# Stage 1: Builder (gradle:8.14-jdk21)
# Stage 2: Runtime (eclipse-temurin:21-jre-alpine)
# Result: ~150MB
```

```bash
# Build
docker build -t seatreservation:latest .

# Run
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mariadb://mariadb:3306/seatres \
  -e SPRING_DATA_REDIS_HOST=redis \
  seatreservation:latest
```

### Health Checks

**Dockerfile**: Each backend instance includes a HEALTHCHECK that validates `/actuator/health` every 10s (30s startup grace period, 5 retries before marking unhealthy).

**docker-compose.dev.yml**:
- `backend-1` and `backend-2` have healthcheck configured to test `/actuator/health`
- `nginx` waits for both backends to be healthy before starting
- Ensures backends are fully initialized before traffic routes to them
- Auto-restarts unhealthy containers

```yaml
backend-1:
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
    interval: 10s
    timeout: 5s
    retries: 5
    start_period: 30s
```

## Performance & Scalability

### Scale to multiple instances

```bash
docker-compose -f docker/docker-compose.dev.yml up --build --scale backend=3
```

`nginx` (`docker/nginx/nginx.conf`) is the only service exposing port 8080 to the host; it does round-robin across backend replicas by resolving their names via Docker's embedded DNS on each request (`resolver 127.0.0.11` + `proxy_pass` with variable, to avoid caching IPs of a single replica at startup).

SSE event fanout between instances works via Redis Pub/Sub (see "Real-time Events" section in `CLAUDE.md` / layers above) — any instance processing a hold/release/reserve publishes to channel `seat-events`, and all instances (including the publisher) receive the message and rebroadcast to their own locally-connected SSE clients.

### Optimizations

- **Virtual threads**: `spring.threads.virtual.enabled=true`
  - Each request/SSE connection uses cheap virtual thread
  - Supports thousands of connections on modest hardware

- **SSE heartbeat**: Keeps connection alive against proxies
  - 15s max latency between heartbeats
  - Comment keepalive to prevent timeouts

- **Pessimistic locking**:
  - `FOR UPDATE` in MariaDB: very efficient for low-medium contention
  - If high contention: consider optimistic locking + retry

- **DB indices**: 
  - `(event_id, status)` → search for available seats
  - `(held_until)` → search for expired in sweep

### Current Limitations

- `SeatLockRegistry` is per JVM → still just a local fail-fast optimization; cross-instance correctness is guaranteed by `SELECT ... FOR UPDATE` in DB, not the in-memory lock (acceptable tradeoff, no changes in this plan)

- No read cache → every GET /seats hits DB
  - Solution: Redis cache with short TTL

- SSE without replay buffer → reconnecting client loses events during disconnection (resolved with full map resync, not event history)

## Logging

Spring Boot defaults to INFO. To see SQL:
```properties
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| "No active transaction" | @Transactional on private method | Use ObjectProvider for proxy |
| "Connection refused" | MariaDB not running | `docker-compose up mariadb` |
| "409 Conflict" | Seat already taken | Another client reserved it first |
| "410 Gone" | Hold expired | Select again (max 120s) |
| SSE not connecting | CORS or proxy issue | Check vite.config.ts proxy |
