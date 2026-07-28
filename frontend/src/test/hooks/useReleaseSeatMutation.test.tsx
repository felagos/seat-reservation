import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useReleaseSeatMutation } from '../../hooks/useReleaseSeatMutation'
import { releaseSeat } from '../../lib/api'
import { seatsKey } from '../../lib/queryKeys'
import type { Seat } from '../../types'

vi.mock('../../lib/api', () => ({ releaseSeat: vi.fn() }))

function wrapper(queryClient: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

const heldSeat = (overrides?: Partial<Seat>): Seat => ({
  id: 1,
  rowLabel: 'A',
  seatNumber: '1',
  status: 'HELD',
  heldByMe: true,
  expiresAt: '2026-01-01T00:00:10Z',
  ...overrides,
})

describe('useReleaseSeatMutation', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    vi.mocked(releaseSeat).mockReset()
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  })

  it('reverts the released seat to AVAILABLE and clears heldByMe/expiresAt', async () => {
    queryClient.setQueryData<Seat[]>(seatsKey, [heldSeat({ id: 1 }), heldSeat({ id: 2 })])
    vi.mocked(releaseSeat).mockResolvedValue(undefined)

    const { result } = renderHook(() => useReleaseSeatMutation(), { wrapper: wrapper(queryClient) })
    result.current.mutate(1)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const seats = queryClient.getQueryData<Seat[]>(seatsKey)!
    expect(seats.find((s) => s.id === 1)).toMatchObject({ status: 'AVAILABLE', heldByMe: false, expiresAt: undefined })
    expect(seats.find((s) => s.id === 2)).toMatchObject({ status: 'HELD', heldByMe: true })
  })

  it('calls onError with the thrown error when the mutation fails', async () => {
    vi.mocked(releaseSeat).mockRejectedValue(new Error('not owned'))
    const onError = vi.fn()

    const { result } = renderHook(() => useReleaseSeatMutation({ onError }), { wrapper: wrapper(queryClient) })
    result.current.mutate(1)

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(onError).toHaveBeenCalledWith(new Error('not owned'))
  })

  it('passes the seatId through to releaseSeat', async () => {
    vi.mocked(releaseSeat).mockResolvedValue(undefined)

    const { result } = renderHook(() => useReleaseSeatMutation(), { wrapper: wrapper(queryClient) })
    result.current.mutate(9)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(releaseSeat).toHaveBeenCalledWith(9)
  })
})
