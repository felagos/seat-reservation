import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useHoldSeatMutation } from '../../hooks/useHoldSeatMutation'
import { holdSeat } from '../../lib/api'
import { seatsKey } from '../../lib/queryKeys'
import type { Seat } from '../../types'

vi.mock('../../lib/api', () => ({ holdSeat: vi.fn() }))

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

describe('useHoldSeatMutation', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    vi.mocked(holdSeat).mockReset()
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  })

  it('marks the held seat as HELD and heldByMe, setting the expiry from the response', async () => {
    queryClient.setQueryData<Seat[]>(seatsKey, [seat({ id: 1 }), seat({ id: 2 })])
    vi.mocked(holdSeat).mockResolvedValue({ seatId: 1, rowLabel: 'A', seatNumber: '1', expiresAt: '2026-01-01T00:00:10Z' })

    const { result } = renderHook(() => useHoldSeatMutation(), { wrapper: wrapper(queryClient) })
    result.current.mutate(1)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const seats = queryClient.getQueryData<Seat[]>(seatsKey)!
    expect(seats.find((s) => s.id === 1)).toMatchObject({ status: 'HELD', heldByMe: true, expiresAt: '2026-01-01T00:00:10Z' })
    expect(seats.find((s) => s.id === 2)).toMatchObject({ status: 'AVAILABLE', heldByMe: false })
  })

  it('leaves other seats untouched', async () => {
    queryClient.setQueryData<Seat[]>(seatsKey, [seat({ id: 1 }), seat({ id: 2 })])
    vi.mocked(holdSeat).mockResolvedValue({ seatId: 1, rowLabel: 'A', seatNumber: '1', expiresAt: 'x' })

    const { result } = renderHook(() => useHoldSeatMutation(), { wrapper: wrapper(queryClient) })
    result.current.mutate(1)
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const other = queryClient.getQueryData<Seat[]>(seatsKey)!.find((s) => s.id === 2)!
    expect(other.status).toBe('AVAILABLE')
  })

  it('calls onError with the thrown error when the mutation fails', async () => {
    vi.mocked(holdSeat).mockRejectedValue(new Error('seat taken'))
    const onError = vi.fn()

    const { result } = renderHook(() => useHoldSeatMutation({ onError }), { wrapper: wrapper(queryClient) })
    result.current.mutate(1)

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(onError).toHaveBeenCalledWith(new Error('seat taken'))
  })

  it('passes the seatId through to holdSeat', async () => {
    vi.mocked(holdSeat).mockResolvedValue({ seatId: 7, rowLabel: 'B', seatNumber: '2', expiresAt: 'x' })

    const { result } = renderHook(() => useHoldSeatMutation(), { wrapper: wrapper(queryClient) })
    result.current.mutate(7)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(holdSeat).toHaveBeenCalledWith(7)
  })
})
