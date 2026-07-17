# Common Pitfalls

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
