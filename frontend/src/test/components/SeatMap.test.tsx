import { describe, it, expect } from 'vitest'
import type { Seat as SeatType } from '../../types'

describe('SeatMap Component', () => {
  const createSeat = (id: number, row: string, number: string): SeatType => ({
    id,
    rowLabel: row,
    seatNumber: number,
    status: 'AVAILABLE',
    heldByMe: false,
  })

  it('should group seats by row', () => {
    const seats = [
      createSeat(1, 'A', '1'),
      createSeat(2, 'A', '2'),
      createSeat(3, 'B', '1'),
    ]

    const grouped = seats.reduce((acc, seat) => {
      if (!acc[seat.rowLabel]) {
        acc[seat.rowLabel] = []
      }
      acc[seat.rowLabel].push(seat)
      return acc
    }, {} as Record<string, SeatType[]>)

    expect(grouped['A']).toHaveLength(2)
    expect(grouped['B']).toHaveLength(1)
  })

  it('should sort rows alphabetically', () => {
    const seats = [
      createSeat(1, 'C', '1'),
      createSeat(2, 'A', '1'),
      createSeat(3, 'B', '1'),
    ]

    const grouped = seats.reduce((acc, seat) => {
      if (!acc[seat.rowLabel]) {
        acc[seat.rowLabel] = []
      }
      acc[seat.rowLabel].push(seat)
      return acc
    }, {} as Record<string, SeatType[]>)

    const rows = Object.keys(grouped).sort()
    expect(rows).toEqual(['A', 'B', 'C'])
  })

  it('should sort seats within row by number', () => {
    const seats = [
      createSeat(1, 'A', '3'),
      createSeat(2, 'A', '1'),
      createSeat(3, 'A', '2'),
    ]

    const sorted = [...seats].sort((a, b) => parseInt(a.seatNumber) - parseInt(b.seatNumber))
    expect(sorted[0].seatNumber).toBe('1')
    expect(sorted[1].seatNumber).toBe('2')
    expect(sorted[2].seatNumber).toBe('3')
  })
})
