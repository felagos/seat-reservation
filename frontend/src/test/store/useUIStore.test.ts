import { describe, it, expect, beforeEach } from 'vitest'
import { useUIStore } from '../../store/useUIStore'

describe('useUIStore', () => {
  beforeEach(() => {
    useUIStore.setState({ error: undefined })
  })

  it('starts with no error', () => {
    expect(useUIStore.getState().error).toBeUndefined()
  })

  it('setError sets the error message', () => {
    useUIStore.getState().setError('Please select at least one seat')

    expect(useUIStore.getState().error).toBe('Please select at least one seat')
  })

  it('clearError resets the error to undefined', () => {
    useUIStore.getState().setError('some error')

    useUIStore.getState().clearError()

    expect(useUIStore.getState().error).toBeUndefined()
  })
})
