# Frontend Tests

## Overview

Test suite for React 19 + TypeScript seat reservation frontend. Covers hooks, components, and API client.

## Running Tests

### All tests
```bash
cd frontend
bun test
```

### Watch mode (re-run on file change)
```bash
bun test --watch
```

### UI mode (browser dashboard)
```bash
bun test --ui
```

### Specific test file
```bash
bun test src/test/hooks/useSeatStream.test.ts
bun test src/test/components/Seat.test.tsx
```

### Test coverage
```bash
bun test --coverage
```

## Test Structure

### Setup

**vitest.config.ts** - Vitest configuration with jsdom environment and setup file
**src/test/setup.ts** - Global setup: ResizeObserver mock, testing-library setup

### Hook Tests

**useSeatStream.test.ts** (`src/test/hooks/useSeatStream.test.ts`)
- EventSource setup and cleanup
- Event listener registration
- SSE message handling (seat-held, seat-released, seat-reserved)
- Client ID comparison (heldByMe)
- Connection error handling

**useSeatsQuery.test.ts** (`src/test/hooks/useSeatsQuery.test.ts`)
- Initial fetch via react-query
- API error handling
- Query key correctness

**useHoldSeatMutation.test.ts** (`src/test/hooks/useHoldSeatMutation.test.ts`)
- Hold seat mutation success
- Error callback on failure
- Event ID passed to API

### Component Tests

**Seat.test.tsx** (`src/test/components/Seat.test.tsx`)
- Render available seat
- Click to hold available seat
- Click to release held seat
- Prevent click on seat held by other
- Prevent click on reserved seat
- Tooltip label display
- Variant (filled vs outlined) based on ownership

**SeatMap.test.tsx** (`src/test/components/SeatMap.test.tsx`)
- Stage label rendering
- Group seats by row
- Sort rows alphabetically
- Sort seats within row numerically
- Pass callbacks to Seat components
- Handle empty seat list

### API Client Tests

**api.test.ts** (`src/test/lib/api.test.ts`)
- getSeatMap() with correct URL and headers
- holdSeat() POST to correct endpoint
- holdMultipleSeats() with seat ID array
- releaseSeat() DELETE request
- confirmReservation() with seat IDs
- Error handling (throw on non-ok response)

## Test Patterns

### Rendering with QueryClient
```tsx
const wrapper = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
)
const { result } = renderHook(() => useSeatsQuery(1), { wrapper })
```

### Mocking EventSource
```ts
const mockEventSource = vi.fn()
global.EventSource = mockEventSource
const eventSourceInstance = { addEventListener: vi.fn(), close: vi.fn() }
mockEventSource.mockReturnValue(eventSourceInstance)
```

### User interactions
```tsx
const user = userEvent.setup()
await user.click(screen.getByRole('button'))
```

### Async waiting
```tsx
await waitFor(() => {
  expect(result.current.isLoading).toBe(false)
})
```

## Debugging

### Show all queries/mutations in console
```ts
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false },
  },
})
```

### Print screen state
```ts
const { debug } = render(<Component />)
debug()
```

### View test execution
```bash
bun test --reporter=verbose
```

## Coverage

Current test suite covers:
- ✅ Seat component logic (properties, state)
- ✅ SeatMap grouping and sorting logic

Future additions:
- React component rendering tests (requires jsdom setup in Bun/Vitest)
- Hook logic (useSeatStream, useSeatsQuery, mutations)
- API client (endpoints, error handling)
- Full component interactions (clicks, user events)
- App integration tests
- ReservationBar countdown logic
- HoldCountdown timer
- useSeatStream reconnection logic
- Zustand store persistence

## Dependencies

Installed in package.json:
- **vitest** - Test runner (Jest-compatible)
- **@testing-library/react** - React component testing
- **@testing-library/user-event** - Simulate user input
- **@testing-library/jest-dom** - DOM matchers
- **jsdom** - DOM implementation for Node.js
- **@vitest/ui** - Browser-based test dashboard

Run `bun install` to fetch all devDependencies.
