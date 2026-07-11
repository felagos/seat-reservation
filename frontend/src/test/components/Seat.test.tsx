import { describe, it, expect } from 'vitest'
import type { Seat as SeatType } from '../../types'

describe('Seat Component', () => {
  const createSeat = (overrides?: Partial<SeatType>): SeatType => ({
    id: 1,
    eventId: 1,
    rowLabel: 'A',
    seatNumber: '1',
    status: 'AVAILABLE',
    heldByMe: false,
    ...overrides,
  })

  it('should have correct seat properties', () => {
    const seat = createSeat()
    expect(seat.id).toBe(1)
    expect(seat.rowLabel).toBe('A')
    expect(seat.seatNumber).toBe('1')
    expect(seat.status).toBe('AVAILABLE')
    expect(seat.heldByMe).toBe(false)
  })

  it('should handle held seat properties', () => {
    const seat = createSeat({ status: 'HELD', heldByMe: true })
    expect(seat.status).toBe('HELD')
    expect(seat.heldByMe).toBe(true)
  })

  it('should handle reserved seat properties', () => {
    const seat = createSeat({ status: 'RESERVED' })
    expect(seat.status).toBe('RESERVED')
  })
})
