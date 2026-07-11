# Backend - Sistema de Reserva de Asientos

Spring Boot 4.1.0 con Java 21 - API REST + SSE para reserva de asientos en tiempo real.

## Arquitectura

### Capas

```
web/                    → REST controllers + DTOs
  ├── SeatMapController
  ├── SeatHoldController
  └── ReservationController

service/                → Business logic
  ├── SeatHoldService (core)
  ├── SeatLockRegistry (concurrencia)
  └── SeatSweepService (expiración)

domain/                 → Entidades JPA
  ├── Event
  ├── Seat
  ├── Reservation
  ├── SeatStatus enum
  └── ReservationStatus enum

repository/             → JPA repositories
  ├── EventRepository
  ├── SeatRepository (con pessimistic locking)
  └── ReservationRepository

sse/                    → Server-Sent Events + fanout Redis
  ├── SseBroadcaster
  ├── SseController
  ├── SseHeartbeatScheduler
  ├── SeatEventListener (publica a Redis)
  ├── RedisSeatEventSubscriber (recibe de Redis, reenvía local)
  └── SeatEventMessage (envelope eventId/eventName/payload)

config/
  └── RedisConfig (ChannelTopic + RedisMessageListenerContainer)

event/                  → Domain events
  ├── SeatHeldEvent
  ├── SeatReleasedEvent
  └── SeatReservedEvent

exception/              → Excepciones personalizadas
  ├── SeatLockTimeoutException
  ├── SeatUnavailableException
  ├── SeatNotOwnedException
  └── HoldExpiredException
```

## Concurrencia: Pessimistic Locking + Queue + Double-Check

### Flujo: Seleccionar asiento (hold)

1. **Lock en memoria** (`SeatLockRegistry`):
   - `ConcurrentHashMap<Long, ReentrantLock>` por `seatId`
   - `tryLock(timeout)` → fail-fast con 409 si timeout

2. **Pessimistic lock DB**:
   - `@Lock(LockModeType.PESSIMISTIC_WRITE)` en JPA
   - `SELECT ... FOR UPDATE` en Postgres
   - Serializa contra múltiples instancias backend

3. **Double-check**:
   - Re-verifica `status`, `heldBy`, `heldUntil` inmediatamente tras leer
   - Trata lazy-expiry aquí: si `held_until < now`, trata como AVAILABLE
   - Lanza `SeatUnavailableException` si no es tomable

4. **Transacción**:
   - Muta: status = HELD, held_by = clientId, held_until = now + TTL
   - Publica evento de dominio
   - Commit libera DB lock

5. **SSE after-commit (fanout multi-instancia vía Redis)**:
   - `SeatEventListener` con `@TransactionalEventListener(AFTER_COMMIT)`
   - Garantiza que SSE solo refleja estado comprometido
   - Publica `SeatEventMessage` como JSON al canal Redis `seat-events` (no llama a `SseBroadcaster` directo)
   - **Todas** las instancias backend están suscritas a ese canal (`RedisConfig` + `RedisSeatEventSubscriber`)
   - Cada instancia recibe el mensaje y llama a su propio `SseBroadcaster.broadcast()` local, entregando a sus clientes conectados
   - Así, un hold procesado en la instancia 2 notifica igual a un cliente conectado por SSE a la instancia 1

### Código clave

```java
// En SeatHoldService.hold()
List<Long> sorted = seatIds.stream().sorted().collect(Collectors.toList());
return lockRegistry.withLocks(sorted, lockTimeoutMs, () ->
    selfProvider.getObject().doHoldTx(eventId, sorted, clientId)
);

// En doHoldTx()
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
            // ... trata como disponible
        } else {
            throw new SeatUnavailableException(seat.getId());
        }
    }
    return responses;
}
```

## Endpoints

Dinámicos por evento. Demo usa **eventId=1**.

### Asientos

**GET** `/api/events/{eventId}/seats`
```
Devuelve: List<SeatDto> ordenado por rowLabel asc, seatNumber asc
Headers: X-Client-Id (opcional, para marcar held-by-me)
Response: 200 OK
```

**POST** `/api/events/{eventId}/seats/{seatId}/hold`
```
Selecciona 1 asiento
Headers: X-Client-Id (requerido)
Response: 200 {seatId, rowLabel, seatNumber, expiresAt}
         409 Conflict (no disponible, timeout, etc.)
```

**POST** `/api/events/{eventId}/seats/hold`
```
Selecciona N asientos
Headers: X-Client-Id
Body: {seatIds: [1, 2, 3]}
Response: 200 List<{seatId, rowLabel, seatNumber, expiresAt}>
         409 Conflict (alguno no disponible)
```

**DELETE** `/api/events/{eventId}/seats/{seatId}/hold`
```
Libera un asiento (solo owner puede)
Headers: X-Client-Id
Response: 204 No Content
         403 Forbidden (no es propietario)
         404 Not Found
```

### Reservas

**POST** `/api/events/{eventId}/reservations`
```
Confirma reserva de múltiples asientos
Headers: X-Client-Id
Body: {seatIds: [1, 2, 3]}
Response: 200 {id, holderId, status}
         409 Conflict (no todos held por este cliente)
         410 Gone (hold expirado)
```

### SSE

**GET** `/api/events/{eventId}/stream`
```
EventSource stream (text/event-stream)
Eventos:
  - seat-held: {seatId, heldBy, expiresAt}
  - seat-released: {seatId}
  - seat-reserved: {seatId, reservationId}
Mantiene alive con heartbeat cada 15s
```

## Configuración

`application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/seatres
spring.datasource.username=postgres
spring.datasource.password=postgres

spring.jpa.hibernate.ddl-auto=validate
spring.threads.virtual.enabled=true

# Redis (fanout de eventos SSE entre instancias)
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Timeouts y intervals
seat.hold.ttl-seconds=120
seat.lock.timeout-ms=3000
seat.sweep.interval-ms=15000
```

## Inicialización (docker/init-db/init-demo.sql + servicio db-init)

Schema y seed ya no viven en Java. `docker/init-db/init-demo.sql` posee ambos:
1. **Schema**: `CREATE TABLE IF NOT EXISTS` para events, reservations, seats + índices `idx_event_status`, `idx_held_until`
2. **Truncate + reset**: `TRUNCATE TABLE seats, reservations, events RESTART IDENTITY CASCADE`
3. **Seed**: crea Event id=1 + 50 Seats (A1-E10)

Ejecutado por el servicio `db-init` en `docker/docker-compose.dev.yml` (`psql -f init-demo.sql`), que corre **cada vez que se hace `docker-compose up`** (no solo la primera vez que se crea el volumen). Orden de arranque: `postgres` (healthy) → `db-init` (corre y termina) → `backend` (arranca).

Como el schema lo posee el SQL, el backend ya no lo crea/altera: `spring.jpa.hibernate.ddl-auto=validate`. Para correr el backend local (`./gradlew bootRun` / `make backend-dev`) contra un Postgres que aún no tiene el schema, primero:
```bash
docker-compose -f ../docker/docker-compose.dev.yml up postgres db-init
```

## Logging

**Stack**: SLF4J + Log4j2 (no Logback)

**Build**: Flag `-parameters` en compilador para que Spring resuelva @PathVariable/@RequestParam sin explícitos

**Niveles por package** (configurable en `log4j2.xml` si agregado):
- `com.seatreservation.service` → DEBUG (SeatHoldService)
- `com.seatreservation.repository` → WARN
- `org.springframework` → INFO

## Base de datos

### Schema (creado por docker/init-db/init-demo.sql, ver sección Inicialización)

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
  held_until TIMESTAMP,        -- NULL si no held
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

## Desarrollo local

### Prerequisitos
- Java 21+
- Gradle 8.14+ (o usa `./gradlew` bundled)
- PostgreSQL 16+ (o Docker)

### Build

```bash
cd backend
./gradlew build -x test    # Compila sin tests
./gradlew clean           # Limpia artifacts
```

### Run

```bash
# Con PostgreSQL + schema/seed + Redis en Docker
docker-compose -f ../docker/docker-compose.dev.yml up postgres db-init redis

# Ejecuta backend
./gradlew bootRun
# Escucha en http://localhost:8080/api
```

### Test (futuro)
```bash
./gradlew test
```

## Docker

### Build multi-stage

```dockerfile
# Stage 1: Builder (gradle:8.14-jdk21)
# Stage 2: Runtime (eclipse-temurin:21-jre-alpine)
# Resultado: ~150MB
```

```bash
# Build
docker build -t seatreservation:latest .

# Run
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/seatres \
  -e SPRING_DATA_REDIS_HOST=redis \
  seatreservation:latest
```

## Performance & Escalabilidad

### Escalar a múltiples instancias

```bash
docker-compose -f docker/docker-compose.dev.yml up --build --scale backend=3
```

`nginx` (`docker/nginx/nginx.conf`) es el único servicio que expone el puerto 8080 al host; hace round-robin sobre las réplicas de `backend` resolviendo su nombre vía el DNS embebido de Docker en cada request (`resolver 127.0.0.11` + `proxy_pass` con variable, para no cachear las IPs de una sola réplica al arrancar).

El fanout de eventos SSE entre instancias funciona vía Redis Pub/Sub (ver sección "Real-time Events" en `CLAUDE.md` / capas más arriba) — cualquier instancia que procese un hold/release/reserve publica al canal `seat-events`, y todas las instancias (incluida la publicadora) reciben el mensaje y lo reenvían a sus propios clientes SSE conectados localmente.

### Optimizaciones

- **Virtual threads**: `spring.threads.virtual.enabled=true`
  - Cada request/SSE connection usa hilo virtual barato
  - Soporta miles de conexiones en hardware modesto

- **SSE heartbeat**: Mantiene alive contra proxies
  - 15s de latencia máxima entre heartbeats
  - Comentario keepalive para evitar timeouts

- **Pessimistic locking**:
  - `FOR UPDATE` en Postgres: muy eficiente para contención baja-media
  - Si contención alta: considera optimistic locking + retry

- **Índices DB**: 
  - `(event_id, status)` → búsqueda de asientos disponibles
  - `(held_until)` → búsqueda de expirados en sweep

### Limitaciones actuales

- `SeatLockRegistry` es por JVM → sigue siendo solo una optimización de fail-fast local; la corrección entre instancias la garantiza `SELECT ... FOR UPDATE` en DB, no el lock en memoria (tradeoff aceptado, sin cambios en este plan)

- No hay caché de reads → cada GET /seats toca DB
  - Solución: Redis cache con TTL corto

- SSE sin replay buffer → cliente que reconecta pierde eventos ocurridos durante la desconexión (se resuelve con un resync completo del mapa, no con historial de eventos)

## Logging

Spring Boot defaults a INFO. Para ver SQL:
```properties
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

## Troubleshooting

| Problema | Causa | Solución |
|----------|-------|----------|
| "No active transaction" | @Transactional en método private | Usar ObjectProvider para proxy |
| "Connection refused" | PostgreSQL no corriendo | `docker-compose up postgres` |
| "409 Conflict" | Asiento ya tomado | Otro cliente lo reservó primero |
| "410 Gone" | Hold expiró | Vuelve a seleccionar (max 120s) |
| SSE no conecta | CORS o proxy issue | Verificar vite.config.ts proxy |

