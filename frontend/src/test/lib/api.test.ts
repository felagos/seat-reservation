import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getSeatMap, holdSeat, releaseSeat, confirmReservation } from '../../lib/api'
import { http } from '../../lib/http'

vi.mock('../../lib/http', () => ({
  http: { get: vi.fn(), post: vi.fn(), del: vi.fn() },
}))

describe('api', () => {
  beforeEach(() => {
    vi.mocked(http.get).mockReset()
    vi.mocked(http.post).mockReset()
    vi.mocked(http.del).mockReset()
  })

  it('getSeatMap fetches from /seats', async () => {
    vi.mocked(http.get).mockResolvedValue([{ id: 1 }])

    const result = await getSeatMap()

    expect(http.get).toHaveBeenCalledWith('/seats')
    expect(result).toEqual([{ id: 1 }])
  })

  it('holdSeat posts to /seats/:id/hold', async () => {
    vi.mocked(http.post).mockResolvedValue({ seatId: 5, expiresAt: '2026-01-01T00:00:00Z' })

    const result = await holdSeat(5)

    expect(http.post).toHaveBeenCalledWith('/seats/5/hold')
    expect(result.seatId).toBe(5)
  })

  it('releaseSeat deletes /seats/:id/hold', async () => {
    vi.mocked(http.del).mockResolvedValue(undefined)

    await releaseSeat(5)

    expect(http.del).toHaveBeenCalledWith('/seats/5/hold')
  })

  it('confirmReservation posts seatIds to /reservations', async () => {
    vi.mocked(http.post).mockResolvedValue({ id: 1, holderId: 'client-a', status: 'CONFIRMED' })

    await confirmReservation([1, 2, 3])

    expect(http.post).toHaveBeenCalledWith('/reservations', { seatIds: [1, 2, 3] })
  })
})
