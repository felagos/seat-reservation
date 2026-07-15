# SeatLockRegistry — paso a paso

`SeatLockRegistry` es lock en memoria, por instancia JVM. Coordina hilos dentro de mismo proceso backend antes de llegar a DB. **No** da correctitud entre instancias — eso lo hace `SELECT ... FOR UPDATE` en `SeatHoldService`. Rol acá: fail-fast rápido (evita hilos bloqueados esperando lock DB innecesariamente).

Archivo: `backend/src/main/java/com/example/demo/service/SeatLockRegistry.java`

## Estructura interna

```java
ConcurrentHashMap<Long, LockHolder> locks;

LockHolder {
  ReentrantLock lock;
  AtomicInteger refCount;
}
```

- Un `ReentrantLock` por seat ID, creado on-demand (lazy).
- `refCount` cuenta cuántos hilos tienen interés actual en ese lock (esperando o sosteniendo).
- Cuando `refCount` llega a 0, entry se elimina del map (evita crecimiento sin límite).

## Paso a paso: `withLocks(sortedSeatIds, timeoutMs, action)`

1. **Deadline único**: calcula un solo `deadlineNanos` para *todos* los locks del request (no timeout por-lock). Evita que request de N seats espere hasta N × timeoutMs.
2. **Por cada seatId (orden ascendente, ya pre-ordenado por caller)**:
   a. `acquireHolder(seatId)` — get-or-create el `LockHolder`, incrementa `refCount` atómicamente vía `compute()` (nunca corre con `release` evictiendo mismo entry).
   b. `tryLock(remainingNanos)` contra deadline compartido.
   c. Si falla (timeout o interrupt) → `releaseUnacquired` (decrementa refCount, evict si llega a 0) → throw `SeatLockTimeoutException`.
   d. Si éxito → guarda en `acquiredIds`/`acquiredHolders`.
3. **Todos los locks adquiridos** → ejecuta `action.get()` (típicamente `doHoldTx`/`doConfirmTx`, la transacción DB con `FOR UPDATE`).
4. **`finally`**: libera locks en **orden inverso** (reverse de adquisición) — previene deadlock. Cada unlock + `release()` (decrementa refCount, evict si 0).

## Por qué orden ascendente + reverse release

Dos hilos pidiendo seats {3,5} y {5,3} simultáneo, sin orden fijo, pueden deadlockear (cada uno espera lock que otro sostiene). Orden ascendente global elimina ese ciclo. Reverse release es higiene simétrica, no estrictamente necesaria para deadlock-freedom acá pero mantiene simetría lock/unlock.

## Diagrama ASCII — flujo completo

```
Thread A: hold(seatIds=[5,3], clientId)
                │
                ▼
   sorted = [3, 5]   (SeatHoldService ordena antes de llamar)
                │
                ▼
┌─────────────────────────────────────────────────────────────┐
│              SeatLockRegistry.withLocks([3,5], 3000ms, action)│
│                                                                │
│  deadline = now + 3000ms                                      │
│                                                                │
│  seatId=3 ──► acquireHolder(3) ──► locks.compute(3, ...)       │
│                │                     refCount++                │
│                ▼                                               │
│         tryLock(remaining) ──── OK ──► acquiredIds=[3]         │
│                                                                │
│  seatId=5 ──► acquireHolder(5) ──► locks.compute(5, ...)       │
│                │                     refCount++                │
│                ▼                                               │
│         tryLock(remaining) ──── OK ──► acquiredIds=[3,5]       │
│                                                                │
│  ambos locks en mano ─────────────────────────────────────┐   │
│                                                             ▼   │
│                              action.get()                      │
│                    (selfProvider.getObject().doHoldTx(...))    │
│                                │                                │
│                    ┌───────────▼────────────┐                  │
│                    │  @Transactional         │                 │
│                    │  SELECT ... FOR UPDATE  │ ◄── DB pessimist.│
│                    │  double-check status    │     lock, cross-│
│                    │  UPDATE seat rows        │     instance    │
│                    │  publishEvent(...)       │     safety      │
│                    │  COMMIT                  │                 │
│                    └───────────┬────────────┘                  │
│                                │                                │
│  finally: release en orden inverso [5, 3]                      │
│    unlock(5) ──► release(5) ──► refCount-- ──► 0? evict map    │
│    unlock(3) ──► release(3) ──► refCount-- ──► 0? evict map    │
└─────────────────────────────────────────────────────────────┘
                │
                ▼
     return List<SeatHoldResponse>


Thread B (concurrente, pide seat 3 solo):
                │
                ▼
   acquireHolder(3) ──► si Thread A ya tiene lock(3):
                          refCount++ (holder compartido, mismo objeto)
                          tryLock() BLOQUEA hasta:
                            (a) Thread A libera → Thread B adquiere, continúa
                            (b) deadline vence → SeatLockTimeoutException (409)
```

## Puntos clave

- **Fail-fast, no correctitud**: si registry fallara/reiniciara, DB `FOR UPDATE` sigue garantizando exclusión mutua. Registry solo evita que hilo bloqueado en memoria monopolice tiempo esperando algo que DB rechazaría igual.
- **refCount evita memory leak**: sin él, cada seatId alguna vez pedido queda para siempre en el map, aunque nadie lo use más.
- **`compute()` atómico**: incrementar/decrementar refCount junto con lookup/eviction del map previene race donde un hilo evict entry mientras otro está a punto de reusarlo.
- **Reentrant**: mismo hilo puede re-adquirir su propio lock sin deadlockear consigo mismo (no usado activamente hoy, pero es propiedad de `ReentrantLock`).

Ver también [`CLAUDE.md`](../CLAUDE.md) sección "Concurrency: SeatHoldService" para el flujo completo de 3 capas (registry → DB lock → double-check).
