import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useSeatStream } from '../../hooks/useSeatStream'
import { seatsKey } from '../../lib/queryKeys'
import type { Seat } from '../../types'

class FakeEventSource {
  static instances: FakeEventSource[] = []
  listeners = new Map<string, ((event: MessageEvent) => void)[]>()
  closed = false
  url: string

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, handler: (event: MessageEvent) => void) {
    const existing = this.listeners.get(type) ?? []
    existing.push(handler)
    this.listeners.set(type, existing)
  }

  close() {
    this.closed = true
  }

  emit(type: string, data?: unknown) {
    const event = { data: data !== undefined ? JSON.stringify(data) : undefined } as MessageEvent
    for (const handler of this.listeners.get(type) ?? []) {
      handler(event)
    }
  }
}

function wrapper(queryClient: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

const seat = (overrides?: Partial<Seat>): Seat => ({
  id: 1,
  rowLabel: 'A',
  seatNumber: '1',
  status: 'AVAILABLE',
  heldByMe: false,
  ...overrides,
})

describe('useSeatStream', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    FakeEventSource.instances = []
    // @ts-expect-error test double, only the subset of the EventSource API the hook uses
    global.EventSource = FakeEventSource
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('opens exactly one EventSource on mount and closes it on unmount', () => {
    const { unmount } = renderHook(() => useSeatStream(), { wrapper: wrapper(queryClient) })

    expect(FakeEventSource.instances).toHaveLength(1)
    const instance = FakeEventSource.instances[0]
    expect(instance.url).toContain('/seats/stream')
    expect(instance.closed).toBe(false)

    unmount()

    expect(instance.closed).toBe(true)
  })

  it('seat-held marks the seat HELD with the payload expiry, without claiming heldByMe', () => {
    queryClient.setQueryData<Seat[]>(seatsKey, [seat({ id: 1 }), seat({ id: 2 })])
    renderHook(() => useSeatStream(), { wrapper: wrapper(queryClient) })

    FakeEventSource.instances[0].emit('seat-held', { seatId: 1, expiresAt: '2026-01-01T00:00:10Z' })

    const seats = queryClient.getQueryData<Seat[]>(seatsKey)!
    expect(seats.find((s) => s.id === 1)).toMatchObject({ status: 'HELD', expiresAt: '2026-01-01T00:00:10Z' })
    expect(seats.find((s) => s.id === 2)).toMatchObject({ status: 'AVAILABLE' })
  })

  it('seat-released reverts the seat to AVAILABLE and clears heldByMe/expiresAt', () => {
    queryClient.setQueryData<Seat[]>(seatsKey, [seat({ id: 1, status: 'HELD', heldByMe: true, expiresAt: 'x' })])
    renderHook(() => useSeatStream(), { wrapper: wrapper(queryClient) })

    FakeEventSource.instances[0].emit('seat-released', { seatId: 1 })

    const [updated] = queryClient.getQueryData<Seat[]>(seatsKey)!
    expect(updated).toMatchObject({ status: 'AVAILABLE', heldByMe: false, expiresAt: undefined })
  })

  it('seat-reserved marks the seat RESERVED and clears heldByMe/expiresAt', () => {
    queryClient.setQueryData<Seat[]>(seatsKey, [seat({ id: 1, status: 'HELD', heldByMe: true, expiresAt: 'x' })])
    renderHook(() => useSeatStream(), { wrapper: wrapper(queryClient) })

    FakeEventSource.instances[0].emit('seat-reserved', { seatId: 1, reservationId: 42 })

    const [updated] = queryClient.getQueryData<Seat[]>(seatsKey)!
    expect(updated).toMatchObject({ status: 'RESERVED', heldByMe: false, expiresAt: undefined })
  })

  it('only patches the seat matching the payload seatId', () => {
    queryClient.setQueryData<Seat[]>(seatsKey, [seat({ id: 1 }), seat({ id: 2 })])
    renderHook(() => useSeatStream(), { wrapper: wrapper(queryClient) })

    FakeEventSource.instances[0].emit('seat-held', { seatId: 2, expiresAt: 'x' })

    const seats = queryClient.getQueryData<Seat[]>(seatsKey)!
    expect(seats.find((s) => s.id === 1)).toMatchObject({ status: 'AVAILABLE' })
    expect(seats.find((s) => s.id === 2)).toMatchObject({ status: 'HELD' })
  })

  it('invalidates the seats query on reconnect (open after a prior error), but not on a clean open', () => {
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    renderHook(() => useSeatStream(), { wrapper: wrapper(queryClient) })
    const instance = FakeEventSource.instances[0]

    instance.emit('open')
    expect(invalidateSpy).not.toHaveBeenCalled()

    instance.emit('error')
    instance.emit('open')
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: seatsKey })
  })

  it('does not invalidate again on a second open after the error flag was already consumed', () => {
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    renderHook(() => useSeatStream(), { wrapper: wrapper(queryClient) })
    const instance = FakeEventSource.instances[0]

    instance.emit('error')
    instance.emit('open')
    invalidateSpy.mockClear()

    instance.emit('open')
    expect(invalidateSpy).not.toHaveBeenCalled()
  })
})
