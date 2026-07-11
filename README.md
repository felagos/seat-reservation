# Sistema de Reserva de Asientos en Tiempo Real

Sistema clásico de reserva de asientos con actualizaciones en tiempo real usando **Server-Sent Events (SSE)**, implementado con **pessimistic locking**, **queue con timeout** y **double-check** para garantizar que dos usuarios no puedan reservar el mismo asiento simultáneamente.

## Características principales

- ✅ **Real-time updates**: SSE para sincronización instantánea entre navegadores
- ✅ **Concurrencia segura**: Pessimistic locking + queue con timeout + double-check
- ✅ **Hold temporal**: Los asientos se pueden seleccionar (hold) y expirecen automáticamente
- ✅ **Multi-asiento**: Reserva múltiples asientos en una transacción atómica
- ✅ **UI responsiva**: Material UI con colores diferenciados por estado
- ✅ **Desarrollo local con hot-reload**: Vite para frontend, Gradle para backend
- ✅ **Docker**: Imagen multi-stage para producción, compose para desarrollo

## Stack tecnológico

### Backend
- **Java 21** con Spring Boot 4.1.0
- **PostgreSQL 16** para persistencia
- **JPA/Hibernate** con pessimistic locking (`SELECT ... FOR UPDATE`)
- **SSE** vía `SseEmitter` de Spring MVC
- **Redis Pub/Sub** para fanout de eventos SSE entre múltiples instancias backend
- **Virtual threads** para manejo eficiente de conexiones
- **Gradle 8.14** como build system
- **nginx** como load balancer round-robin al escalar backend

### Frontend
- **React 19** + TypeScript
- **Vite 8** como bundler
- **Material UI 6** para componentes
- **Bun** como package manager
- **EventSource (SSE)** para conexión en tiempo real
- **Vite proxy** para desarrollo local

## Iniciar

### Opción 1: Docker (recomendado - sin dependencias locales)
```bash
# Terminal 1: PostgreSQL + Backend en Docker
make backend

# Terminal 2: Frontend (Vite)
make frontend

# Abre http://localhost:5173
```

### Opción 2: Desarrollo local con hot-reload
```bash
# Terminal 1: PostgreSQL en Docker + Backend en Gradle (hot-reload)
make backend-dev

# Terminal 2: Frontend (Vite)
make frontend

# Abre http://localhost:5173
```

### Manual
```bash
# Opción 1 - Docker:
docker-compose -f docker/docker-compose.dev.yml up

# Opción 2 - Local:
# Terminal 1: docker-compose -f docker/docker-compose.dev.yml up postgres db-init
# Terminal 2: cd backend && ./gradlew bootRun
# Terminal 3: cd frontend && bun dev
```

El schema y los datos demo (evento id=1 + 50 asientos) ya no los crea el backend: los posee `docker/init-db/init-demo.sql`, ejecutado por el servicio `db-init` cada vez que corre `docker-compose up` (orden: `postgres` → `db-init` → `backend`). Backend usa `ddl-auto=validate`, así que necesita que `db-init` haya corrido al menos una vez contra el Postgres que esté usando.

### Escalar a múltiples instancias backend

```bash
docker-compose -f docker/docker-compose.dev.yml up --build --scale backend=3
```

`backend` ya no expone puerto directo al host — `nginx` (puerto 8080) hace round-robin sobre las réplicas. Los eventos SSE se sincronizan entre instancias vía Redis Pub/Sub, así que un hold procesado por una réplica notifica igual a clientes conectados por SSE a otra réplica. Ver `backend/README.md` sección "Performance & Escalabilidad" para el detalle.

## Makefile commands

| Comando | Descripción |
|---------|-------------|
| `make backend` | PostgreSQL + Backend en Docker (recomendado) |
| `make backend-dev` | PostgreSQL (docker) + Backend (gradle con hot-reload) |
| `make frontend` | Vite dev server |
| `make db-up` | Solo levanta PostgreSQL en Docker |
| `make db-down` | Detiene PostgreSQL + Backend |
| `make build-backend` | Build imagen Docker multi-stage |
| `make dev` | Alias para `backend-dev` |
| `make all` | Muestra instrucciones de inicio |
| `make clean` | Limpia artifacts build |
| `make help` | Muestra ayuda |

## Puertos

- **Backend** (vía nginx): `http://localhost:8080/api`
- **Frontend**: `http://localhost:5173`
- **PostgreSQL**: `localhost:5432`
- **Redis**: interno a la red de Docker (no expuesto al host)

## Estructura del proyecto

```
seat-reservation/
├── backend/                    # Spring Boot application
│   ├── src/main/java/
│   │   └── com/example/demo/
│   │       ├── domain/         # Entities (Event, Seat, Reservation)
│   │       ├── repository/     # JPA repositories
│   │       ├── service/        # Business logic (SeatHoldService, SeatLockRegistry)
│   │       ├── web/            # REST controllers
│   │       ├── sse/            # SSE handlers + fanout Redis (SeatEventListener, RedisSeatEventSubscriber)
│       ├── config/         # RedisConfig (Pub/Sub topic + listener container)
│   │       ├── event/          # Domain events
│   │       └── exception/      # Custom exceptions
│   ├── Dockerfile              # Multi-stage build
│   └── build.gradle.kts
│
├── frontend/                   # React + Vite application
│   ├── src/
│   │   ├── components/         # React components (SeatMap, Seat, etc.)
│   │   ├── hooks/              # Custom hooks (useSeatMap, useSeatStream)
│   │   ├── lib/                # Utilities (API client, clientId)
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── vite.config.ts
│   └── package.json
│
├── docker/
│   ├── docker-compose.dev.yml  # PostgreSQL + db-init + Redis + Backend (escalable) + nginx
│   ├── init-db/
│   │   └── init-demo.sql       # Schema (CREATE TABLE) + seed demo data
│   └── nginx/
│       └── nginx.conf          # Load balancer round-robin sobre réplicas de backend
│
└── Makefile                    # Build automation

```

## Casos de Uso

### Caso 0: Inicialización y carga del mapa de asientos

```
docker-compose up          db-init container           Base de datos              Usuario abre app         Frontend
────────────────────────────────────────────────────────────────────────────────────────────────────────────────
     │
     ├─ postgres healthy
     ├─ db-init: psql -f init-demo.sql
     │                            │
     │                            ├─ CREATE TABLE IF NOT EXISTS events/reservations/seats
     │                            ├─ TRUNCATE ... RESTART IDENTITY CASCADE
     │
     │                            ├─ CREATE Event(id=1, Demo Concert)
     │                            │  INSERT INTO events
     │
     │                            ├─ CREATE 50 Seats (5 filas x 10)
     │                            │  INSERT INTO seats (all status=AVAILABLE)
     │
     │                            ├─ COMMIT
     │
     ├─ db-init exits 0 ──────────◄───────── Inicialización completada
     ├─ backend arranca (ddl-auto=validate)
     │
                                                    Usuario accede http://localhost:5173
                                                           │
                                                           ├─ App.tsx monta
                                                           │
                                                           ├─ useSeatMap(1)
                                                           │  useEffect → GET /api/events/1/seats
     │                                                                          │
     │────────────────────────────────▶ SELECT * FROM seats WHERE event_id = 1
     │
                                                           ◄────────────────── 200 OK [
                                                                {id:1, row:A, seat:1, status:AVAILABLE},
                                                                {id:2, row:A, seat:2, status:AVAILABLE},
                                                                ...,
                                                                {id:50, row:E, seat:10, status:AVAILABLE}
                                                              ]
                                                           │
                                                           ├─ dispatch({ type: 'INIT', payload: [...] })
                                                           │
                                                           ├─ useSeatStream(1)
                                                           │  new EventSource('/api/events/1/stream')
     │                                                                          │
     │◄──────────────────────────────── GET /api/events/1/stream (text/event-stream)
     │
                                                           ├─ UI renderiza grid (5 filas x 10 asientos)
                                                           │  Todos los asientos = VERDE (AVAILABLE, clickeables)
                                                           │
                                                           └─ EventSource escuchando:
                                                              addEventListener('seat-held', ...)
                                                              addEventListener('seat-reserved', ...)
                                                              addEventListener('seat-released', ...)
```

**Resultado**: Backend trunca BD e inicializa demo en startup. Usuario abre app y ve mapa limpio con 50 asientos disponibles. EventSource listo para cambios en tiempo real.

---

### Caso 1: Un usuario selecciona un asiento

```
Usuario A (navegador 1)          Backend                Base de datos
─────────────────────────────────────────────────────────────────────
     │
     ├─ Click Asiento 3 ─────────▶ POST /api/.../seats/3/hold
     │                             │
     │                             ├─ SeatLockRegistry.tryLock(3000ms)
     │                             │  ✓ Lock adquirido
     │                             │
     │                             ├─ @Transactional doHoldTx()
     │                             │  ├─ SELECT ... FOR UPDATE ───────▶ Row 3 bloqueado
     │                             │  │
     │                             │ Double-check:
     │                             │  ├─ Status = AVAILABLE ✓
     │                             │  ├─ Mutar estado ────────────────▶ status=HELD
     │                             │  │                                 held_by=UUID-A
     │                             │  │                                 held_until=now+120s
     │                             │  └─ Evento: SeatHeldEvent(...)
     │                             │
     │                             ├─ Commit transacción ─────────────▶ Row 3 desbloqueado
     │                             │  ├─ EventListener @AFTER_COMMIT
     │                             │  └─ Redis publish("seat-events") → cada instancia: SseBroadcaster.broadcast() local
     │                             │
     │                    ◄────────┤ SSE: {seat-held, seatId:3, ...}
     │                             │
     ◄────────────────────────────── 200 OK {expiresAt: "2026-07-11T02:20:00Z"}
     │
     ├─ UI: Asiento 3 naranja (held-by-me)
     │
     └─ HoldCountdown: 120s ⏱️  ⏱️  ⏱️

Usuario B (navegador 2)          Backend                SSE stream
─────────────────────────────────────────────────────────────────────
     │                             │
     │ EventSource conectado ◄─────┤ SSE: {seat-held, seatId:3, heldBy:UUID-A}
     │                             │
     ├─ UI: Asiento 3 gris (held-by-other)
     │
```

**Resultado**: Asiento 3 aparece en naranja (A) y gris (B) en tiempo real.

---

### Caso 2: Dos usuarios intentan seleccionar el mismo asiento simultáneamente

```
Usuario A (UUID-A)               Usuario B (UUID-B)          Backend
─────────────────────────────────────────────────────────────────────
     │                                │
     ├─ Click Asiento 7 ─────────────▶ Click Asiento 7
     │  (t=0ms)                       │  (t=1ms)
     │                                │
     ├─ holdSeat(7) ────────────────────────────────▶ Lock memoria: tryLock(7)
     │                                               ✓ A adquiere lock
     │
     ├─ holdSeat(7) ──────────────────────────────▶ Lock memoria: tryLock(7)
     │                                             ✗ B espera (bloqueado)
     │
     ├─ POST /seats/7/hold ────────────────────────▶ SELECT FOR UPDATE (7)
     │                                             ✓ A tiene lock DB
     │
     ├─ Double-check: AVAILABLE ✓ ─────────────────▶ status = HELD
     │                                             │ held_by = UUID-A
     │                                             │ Commit
     │                                             │
     │                                             ├─ Liberar lock (7) DB
     │                                             │
     │                                             └─ Release lock (7) memoria
     │                                                B: tryLock(7) ✓ adquiere
     │
     ◄───────────────────────────────────────────── 200 OK
     │ Asiento 7 = NARANJA (held-by-me)
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

**Resultado**: Solo A consigue el asiento. B obtiene 409 (sin doble reserva).

---

### Caso 3: Usuario confirma reserva

```
Usuario A (hold 3 asientos)      Backend                Base de datos
─────────────────────────────────────────────────────────────────────
     │
     ├─ [3, 5, 7] Asientos naranja
     │  (todos held-by-me)
     │
     ├─ Click "Confirmar Reserva" ──▶ POST /api/.../reservations
     │                               │
     │                               ├─ SeatLockRegistry.tryLock([3,5,7])
     │                               │  ✓ Todos los locks adquiridos (orden: 3,5,7)
     │                               │
     │                               ├─ @Transactional doConfirmTx()
     │                               │  ├─ SELECT FOR UPDATE WHERE id IN (3,5,7)
     │                               │  │  ─────────────────────────────────────▶ Rows bloqueados
     │                               │  │
     │                               │  ├─ Double-check cada uno:
     │                               │  │  ├─ Seat 3: HELD + held_by=UUID-A + no expirado ✓
     │                               │  │  ├─ Seat 5: HELD + held_by=UUID-A + no expirado ✓
     │                               │  │  └─ Seat 7: HELD + held_by=UUID-A + no expirado ✓
     │                               │  │
     │                               │  ├─ Crear Reservation(id=123)
     │                               │  └─ Para cada asiento:
     │                               │     └─ status = RESERVED, reservation_id = 123
     │                               │
     │                               ├─ Commit ────────────────────────────────▶ Rows desbloqueados
     │                               │  ├─ EventListener @AFTER_COMMIT
     │                               │  ├─ SeatReservedEvent(3), SeatReservedEvent(5), ...
     │                               │  └─ Redis publish("seat-events") → cada instancia: SseBroadcaster.broadcast() local
     │                               │
     │                    ◄──────────┤ SSE a TODOS: {seat-reserved, seatId:3/5/7}
     │
     ◄────────────────────────────── 200 OK {reservationId: 123}
     │
     ├─ UI: Asientos 3, 5, 7 = ROJO (reserved)
     │       Botón "Confirmar" deshabilitado
     │       Se muestra "Reserva confirmada"
     │

Usuario C (navegador otro)         Backend                SSE stream
─────────────────────────────────────────────────────────────────────
     │                             │
     │ EventSource conectado ◄─────┤ SSE: {seat-reserved, seatId:3}
     │                             │       {seat-reserved, seatId:5}
     │                             │       {seat-reserved, seatId:7}
     │
     ├─ UI: Asientos 3, 5, 7 = ROJO (reserved)
     │       No puede seleccionar
```

**Resultado**: Reserva confirmada atómicamente. Todos ven rojo.

---

### Caso 4: Hold expira automáticamente (después de 120s)

```
Usuario A (hold Asiento 9)       Backend Sweep Job (cada 15s)    Base de datos
────────────────────────────────────────────────────────────────────────────
     │
     ├─ t=0s: Click Asiento 9
     │        status = HELD
     │        held_until = t+120s
     │
     ├─ t=15s: UI muestra "Expira en: 105s" ⏱️
     │
     ├─ t=30s: UI muestra "Expira en: 90s" ⏱️
     │
     ├─ t=120s: UI muestra "Expira en: 0s" ⏱️
     │
     │                                                  │
     │                   t=120s: Sweep job corre ──────▶ SELECT * FROM seats
     │                           WHERE status='HELD'
     │                           AND held_until < now
     │                                                  │
     │                           ├─ SeatLockRegistry.tryLock([9])
     │                           │
     │                           ├─ @Transactional doExpireTx()
     │                           │  ├─ SELECT FOR UPDATE WHERE id=9
     │                           │  │  ───────────────────────────────▶ Row bloqueado
     │                           │  │
     │                           │  ├─ Double-check:
     │                           │  │  ├─ status = HELD ✓
     │                           │  │  └─ held_until < now ✓
     │                           │  │
     │                           │  └─ Mutar: status = AVAILABLE
     │                           │     held_by = NULL
     │                           │     held_until = NULL
     │                           │  ───────────────────────────────▶ Row actualizado
     │                           │
     │                           ├─ Commit
     │                           │  ├─ EventListener @AFTER_COMMIT
     │                           │  └─ SeatReleasedEvent(9)
     │                           │
     │                ◄──────────┤ SSE a TODOS: {seat-released, seatId:9}
     │
     ├─ UI: Asiento 9 se pone VERDE (available)
     │       Contador desaparece
     │       Puede seleccionar de nuevo
     │

Usuario B (navegador otro)        Backend                SSE stream
────────────────────────────────────────────────────────────────────
     │ EventSource conectado ◄────┤ SSE: {seat-released, seatId:9}
     │
     ├─ UI: Asiento 9 se pone VERDE
     │       Puede seleccionar
```

**Resultado**: Hold expira después de 120s. Asiento vuelve a estar disponible para todos.

---

## Flujo de concurrencia

### 1. Seleccionar un asiento (hold)
```
Cliente → POST /api/events/1/seats/3/hold
         ↓
SeatLockRegistry (lock en memoria con timeout)
         ↓
Transacción DB: SELECT ... FOR UPDATE (pessimistic lock)
         ↓
Double-check: ¿status == AVAILABLE? ¿held_until expirado?
         ↓
Actualizar: status = HELD, held_by = clientId, held_until = now + 120s
         ↓
Publicar evento → SSE → Todos los navegadores sse actualizado
```

### 2. Confirmar reserva (confirm)
```
Cliente con asientos held-by-me → POST /api/events/1/reservations
         ↓
SeatLockRegistry + SELECT ... FOR UPDATE (misma secuencia)
         ↓
Double-check: ¿Todos held por este clientId? ¿Aún vigentes?
         ↓
Crear Reservation, actualizar status = RESERVED
         ↓
Evento SSE → Todos ven asiento RESERVED
```

### 3. Expiración automática (sweep)
```
Job periódico (cada 15s) → Busca asientos HELD con held_until < now
         ↓
Para cada asiento: mismo lock + SELECT ... FOR UPDATE
         ↓
Double-check: ¿Aún expirado?
         ↓
Revertir: status = AVAILABLE, held_by = null
         ↓
Evento SSE → Todos ven asiento disponible de nuevo
```

## Frontend: Sincronización SSE

```
1. EventSource abierto → /api/events/1/stream
2. Escucha eventos: seat-held, seat-released, seat-reserved
3. Actualiza estado local vía reducer (useSeatMap)
4. Reconexión automática: refetch completo del mapa
5. UI refleja cambios instantáneamente
```

## Experiencia del usuario

1. Abrir `http://localhost:5173` en navegador
2. Mapa de asientos carga automáticamente (seed de `docker/init-db/init-demo.sql`)
3. Hacer click en un asiento → Se pone **naranja** (held-by-me)
4. El asiento expira después de 120 segundos → Se pone gris (available)
5. Múltiples asientos → Click en botón "Confirmar Reserva"
6. Asiento pasa a **rojo** (reserved) y aparece en otros navegadores

## Endpoints API

Dinámicos por evento. Demo usa **eventId=1** (único evento).

### Asientos
- `GET /api/events/{eventId}/seats` - Mapa completo de asientos
- `POST /api/events/{eventId}/seats/{seatId}/hold` - Seleccionar 1 asiento
- `POST /api/events/{eventId}/seats/hold` - Seleccionar N asientos
- `DELETE /api/events/{eventId}/seats/{seatId}/hold` - Liberar asiento

### Reservas
- `POST /api/events/{eventId}/reservations` - Confirmar reserva (multi-asiento)

### SSE
- `GET /api/events/{eventId}/stream` - EventSource stream
  - Eventos: `seat-held`, `seat-released`, `seat-reserved`

No hay endpoint de admin/reinit por HTTP. El seed (schema + truncate + datos demo) corre vía `docker/init-db/init-demo.sql`, ejecutado por el servicio `db-init` cada vez que se hace `docker-compose up` (ver sección "Iniciar").

## Desarrollo

### Backend
Ver [backend/README.md](backend/README.md)

### Frontend
Ver [frontend/README.md](frontend/README.md)

## Notas de diseño

- `X-Client-Id`: UUID generado por cliente (simplificación, no producción)
- **Resync en reconexión**: Refetch completo (simple y siempre correcto)
- **Sweep**: Expiración perezosa en double-check + job periódico (15s)
- **Virtual threads**: `spring.threads.virtual.enabled=true` para escalabilidad
- **Multi-stage Docker**: Reduce tamaño de imagen ~30-40%

## Escalabilidad

- `SeatLockRegistry` es por JVM → múltiples instancias: DB lock garantiza corrección, pero timeout fail-fast se pierde
- Para producción: Flyway/Liquibase, Spring Security, Redis para eventos cross-instance
