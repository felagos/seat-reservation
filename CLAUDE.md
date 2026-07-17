# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Real-time seat reservation system using **pessimistic locking + queue with timeout + double-check** for concurrency safety. Two users cannot reserve the same seat simultaneously.

**Stack:**
- Backend: Java 21, Spring Boot 4.1.0, MariaDB, Gradle
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

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — covers SeatHoldService concurrency, SSE+Redis fanout, config, DB init, hold expiration, frontend state.

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
- Database init logs now come from the `db-init` container's `mariadb` client output, not Java/Log4j2

## Debugging Tips

**Backend:** Enable SQL logging in `application.properties`:
```
logging.level.org.hibernate.SQL=DEBUG
```

**Frontend:** Check Vite proxy in DevTools Network tab — requests to `/api/...` should show as proxied to `localhost:8080`.

**SSE:** Use browser DevTools (Application > EventSource) to watch incoming messages in real-time.

## References

- `docs/ARCHITECTURE.md` — critical architecture patterns (concurrency, SSE fanout, config, DB init, hold expiration, frontend state)
- `README.md` — full system overview + diagrams
- `backend/README.md` — API endpoints, database schema
- `frontend/README.md` — component architecture, hooks
- `Makefile` — all build/dev commands
