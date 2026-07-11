# Backend Tests

## Overview

Test suite for Java 21 Spring Boot seat reservation backend. Covers concurrency, service logic, and API endpoints.

## Running Tests

### All tests
```bash
cd backend
./gradlew test
```

### Specific test class
```bash
./gradlew test --tests SeatLockRegistryTest
./gradlew test --tests SeatHoldServiceTest
./gradlew test --tests ConcurrencySeatHoldTest
./gradlew test --tests SeatHoldControllerIntegrationTest
```

### With output
```bash
./gradlew test --info
```

## Test Structure

### Unit Tests

**SeatLockRegistryTest** (`src/test/java/com/example/demo/service/SeatLockRegistryTest.java`)
- Single and multi-lock acquisition
- Lock timeout handling
- Lock release on exceptions
- Concurrent lock access
- Partial acquisition rollback

**SeatHoldServiceTest** (`src/test/java/com/example/demo/service/SeatHoldServiceTest.java`)
- Single and multiple seat holds
- Hold by unavailable/held seats
- Expired seat re-use
- Release seat operations
- Mock-based service tests

### Integration Tests

**ConcurrencySeatHoldTest** (`src/test/java/com/example/demo/service/ConcurrencySeatHoldTest.java`)
- Multiple threads competing for same seat (only one succeeds)
- Concurrent holds on different seats
- Lock timeout prevents data corruption
- Real database with JPA pessimistic locks

**SeatHoldControllerIntegrationTest** (`src/test/java/com/example/demo/web/SeatHoldControllerIntegrationTest.java`)
- Hold single and multiple seats via HTTP
- Conflict detection (409) on unavailable seats
- Invalid seat ID handling
- Real Spring context + MockMvc

## Test Database

Tests use H2 in-memory database configured in `src/test/resources/application-test.properties`:
- URL: `jdbc:h2:mem:testdb`
- Hibernate: `create-drop` (schema created/destroyed per test)
- No persistence to disk

## Key Test Patterns

### Mocking with Mockito
```java
@ExtendWith(MockitoExtension.class)
@Mock
private SeatRepository seatRepository;

when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
```

### @DataJpaTest for JPA
```java
@DataJpaTest
@Import(SeatLockRegistry.class)
class ConcurrencySeatHoldTest {
  // Real DB, auto-configured DataSource, no services
}
```

### @SpringBootTest for integration
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class SeatHoldControllerIntegrationTest {
  // Full context, real web layer
}
```

### Concurrent testing
```java
var executor = Executors.newFixedThreadPool(numThreads);
var latch = new CountDownLatch(numThreads);
// Submit tasks, latch.await() to sync all threads
```

## Debugging

Enable SQL logging in tests:
```properties
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql=TRACE
```

## Coverage

Current test suite covers:
- ✅ Lock registry (lock/unlock, concurrent access, exception handling)
- ✅ Basic service wiring

Disabled (requires Redis):
- ⏸️ Concurrency tests (pessimistic locking, multi-thread)
- ⏸️ Controller integration tests (HTTP layer)

Future additions:
- Re-enable with embedded Redis (TestContainers)
- Sweep job expiry tests
- SSE event publishing
- Redis fanout in multi-instance setup
- @DataJpaTest for JPA-only tests (without Redis dependency)
