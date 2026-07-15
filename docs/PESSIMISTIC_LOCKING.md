# PESSIMISTIC_WRITE lock — cómo funciona

`PESSIMISTIC_WRITE` es lock a nivel DB (`SELECT ... FOR UPDATE`), aplicado vía JPA/Hibernate. Es la capa que da correctitud **entre instancias** del backend (multi-replica). Complementa a [`SeatLockRegistry`](./SEAT_LOCK_REGISTRY.md) (lock en memoria, per-JVM, solo fail-fast).

Archivo: `backend/src/main/java/com/example/demo/repository/SeatRepository.java`

## Qué es `PESSIMISTIC_WRITE`

Enum de `jakarta.persistence.LockModeType`. Le dice a Hibernate: al ejecutar el `@Query`, agregar `FOR UPDATE` al SQL generado. Fila queda bloqueada en DB hasta que transacción actual haga `COMMIT` o `ROLLBACK` — cualquier otra transacción que intente `SELECT ... FOR UPDATE` (o `UPDATE`/`DELETE`) sobre misma fila **bloquea** hasta que primera libere.

"Pessimistic" = asume que va a haber conflicto, bloquea de entrada (vs "optimistic" que usa version/timestamp y detecta conflicto al commit).

## Dónde se usa

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
@Query("select s from Seat s where s.id = :id")
Optional<Seat> findByIdForUpdate(@Param("id") Long id);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from Seat s where s.id in :ids order by s.id")
List<Seat> findAllByIdForUpdate(@Param("ids") List<Long> ids);
```

- `findByIdForUpdate` — un seat. Usado en `doReleaseTx`, `doExpireTx`.
- `findAllByIdForUpdate` — múltiples seats, `order by s.id` (mismo orden ascendente que `SeatLockRegistry`, evita deadlock a nivel DB también). Usado en `doHoldTx`, `doConfirmTx`.
- `lock.timeout=3000` hint — si fila ya bloqueada por otra transacción, espera máx 3000ms, después lanza `LockTimeoutException` en vez de bloquear indefinido.

SQL generado (MariaDB):
```sql
SELECT * FROM seats WHERE id IN (3, 5) ORDER BY id FOR UPDATE;
```

## Paso a paso — flujo de un hold

1. `SeatHoldService.hold(seatIds, clientId)` ordena seat IDs ascendente, adquiere locks en memoria vía `SeatLockRegistry.withLocks()`.
2. Con locks de memoria en mano, entra a `doHoldTx()` (`@Transactional`) → abre transacción DB.
3. `seatRepository.findAllByIdForUpdate(seatIds)` ejecuta `SELECT ... FOR UPDATE` → filas bloqueadas en DB hasta commit.
4. **Double-check**: revisa `status`/`held_by`/`held_until` de cada fila — estado pudo cambiar entre el momento en que cliente vio el seat mapa y ahora. Si expiró (`held_until < now`) trata como disponible (lazy expiry). Si otro cliente ya la tiene y no expiró, `SeatUnavailableException`.
5. `UPDATE` filas → `HELD`, `held_by`, `held_until`.
6. `publishEvent(SeatHeldEvent)` (encolado, se dispara AFTER_COMMIT).
7. `COMMIT` → libera locks DB. Filas visibles para otras transacciones con nuevo estado.
8. `finally` de `withLocks()` libera locks de memoria.

## Por qué dos capas (memoria + DB)

| | SeatLockRegistry (memoria) | PESSIMISTIC_WRITE (DB) |
|---|---|---|
| Alcance | Un JVM/instancia | Todas las instancias (vía DB compartida) |
| Costo | Barato (in-process) | Más caro (round-trip DB, fila bloqueada) |
| Rol | Fail-fast, evita saturar pool de conexiones DB con hilos esperando | Fuente de verdad de correctitud |
| Si se cae/reinicia | Sin impacto en correctitud | Rompe todo |

Sin capa memoria: N hilos concurrentes por mismo seat todos abren transacción y compiten por lock DB — desperdicia conexiones del pool esperando. Con ella: la mayoría de hilos bloquean/fallan barato en memoria, solo el que ya tiene turno abre transacción DB.

Sin capa DB: multi-instancia (nginx round-robin a N backends) — dos requests para mismo seat en dos instancias distintas, cada uno con su propio `SeatLockRegistry` vacío del otro, ambos pasarían el lock de memoria sin saberlo. DB `FOR UPDATE` es lo único que serializa entre instancias.

## Diagrama ASCII — dos instancias, mismo seat

```
                    Cliente A                    Cliente B
                        │                             │
                        ▼                             ▼
                 nginx round-robin              nginx round-robin
                        │                             │
                        ▼                             ▼
              ┌─────────────────┐           ┌─────────────────┐
              │  Backend #1      │           │  Backend #2      │
              │                  │           │                  │
              │ SeatLockRegistry │           │ SeatLockRegistry │
              │ (vacío, no sabe  │           │ (vacío, no sabe  │
              │  de instancia 2) │           │  de instancia 1) │
              │       │          │           │       │          │
              │  lock(seat=7) OK │           │  lock(seat=7) OK │  ◄── ambos pasan,
              │       │          │           │       │          │      locks son locales
              │       ▼          │           │       ▼          │
              │  BEGIN TX        │           │  BEGIN TX        │
              │  SELECT seat=7   │           │  SELECT seat=7   │
              │  FOR UPDATE      │           │  FOR UPDATE      │
              └───────┬──────────┘           └───────┬──────────┘
                      │                               │
                      ▼                               ▼
              ┌───────────────────────────────────────────────┐
              │                MariaDB                          │
              │  fila seat id=7                                 │
              │                                                  │
              │  Backend #1 llega primero ──► lock adquirido     │
              │  Backend #2 llega después ──► BLOQUEADO           │
              │                                (espera hasta 3s)  │
              └───────────────────────────────────────────────┘
                      │                               │
                      ▼                               │
              status==AVAILABLE?                       │
              sí ──► UPDATE HELD                        │
              COMMIT ──► libera fila                     │
                      │                               │
                      │                               ▼
                      │                    lock adquirido (fila libre)
                      │                    double-check: status==HELD ya!
                      │                    (Backend #1 lo cambió)
                      │                    ──► SeatUnavailableException (409)
                      ▼                               ▼
              200 OK, seat held               409 Conflict
```

## `LockTimeoutException` vs `SeatLockTimeoutException`

No confundir:
- `SeatLockTimeoutException` — viene de `SeatLockRegistry` (memoria), timeout en `tryLock()`.
- `jakarta.persistence.LockTimeoutException` — viene de DB, timeout en `FOR UPDATE` (hint `lock.timeout=3000`). Ocurre solo si dos instancias distintas compiten (o mismo seat.lock.timeout-ms mal configurado vs DB), porque dentro de una instancia `SeatLockRegistry` ya serializa antes de llegar a DB.

Ambos deben mapear a HTTP 409 (seat ocupado / contención), no 500.

## Referencias

- [`SEAT_LOCK_REGISTRY.md`](./SEAT_LOCK_REGISTRY.md) — capa de lock en memoria
- [`CLAUDE.md`](../CLAUDE.md) — sección "Concurrency: SeatHoldService", visión de las 3 capas completas
