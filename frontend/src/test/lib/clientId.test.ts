import { describe, it, expect, beforeEach } from 'vitest'
import { getOrCreateClientId } from '../../lib/clientId'

describe('getOrCreateClientId', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('creates and persists a new client id when none exists', () => {
    const id = getOrCreateClientId()

    expect(id).toBeTruthy()
    expect(window.localStorage.getItem('seatClientId')).toBe(id)
  })

  it('returns the existing id instead of generating a new one', () => {
    window.localStorage.setItem('seatClientId', 'existing-id')

    const id = getOrCreateClientId()

    expect(id).toBe('existing-id')
  })

  it('returns the same id across repeated calls', () => {
    const first = getOrCreateClientId()
    const second = getOrCreateClientId()

    expect(second).toBe(first)
  })
})
