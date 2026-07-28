import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useConfirmReservationMutation } from '../../hooks/useConfirmReservationMutation'
import { confirmReservation } from '../../lib/api'
import { seatsKey } from '../../lib/queryKeys'

vi.mock('../../lib/api', () => ({ confirmReservation: vi.fn() }))

function wrapper(queryClient: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('useConfirmReservationMutation', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    vi.mocked(confirmReservation).mockReset()
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  })

  it('invalidates the seats query on success', async () => {
    vi.mocked(confirmReservation).mockResolvedValue({ id: 1, holderId: 'client-a', status: 'CONFIRMED' })
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useConfirmReservationMutation(), { wrapper: wrapper(queryClient) })
    result.current.mutate([1, 2])

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: seatsKey })
  })

  it('passes the seatIds through to confirmReservation', async () => {
    vi.mocked(confirmReservation).mockResolvedValue({ id: 1, holderId: 'client-a', status: 'CONFIRMED' })

    const { result } = renderHook(() => useConfirmReservationMutation(), { wrapper: wrapper(queryClient) })
    result.current.mutate([3, 4, 5])

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(confirmReservation).toHaveBeenCalledWith([3, 4, 5])
  })

  it('does not invalidate the seats query and calls onError when the mutation fails', async () => {
    vi.mocked(confirmReservation).mockRejectedValue(new Error('hold expired'))
    const onError = vi.fn()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useConfirmReservationMutation({ onError }), { wrapper: wrapper(queryClient) })
    result.current.mutate([1])

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(onError).toHaveBeenCalledWith(new Error('hold expired'))
    expect(invalidateSpy).not.toHaveBeenCalled()
  })
})
