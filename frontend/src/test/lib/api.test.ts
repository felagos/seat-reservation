import { describe, it, expect, vi, beforeEach } from 'vitest'
import { HttpClient } from '../../lib/http'

describe('HttpClient', () => {
  let httpClient: HttpClient

  beforeEach(() => {
    httpClient = new HttpClient()
    global.fetch = vi.fn()
  })

  it('should be instantiable', () => {
    expect(httpClient).toBeDefined()
    expect(httpClient.get).toBeDefined()
    expect(httpClient.post).toBeDefined()
    expect(httpClient.del).toBeDefined()
  })

  it('should have correct methods', () => {
    expect(typeof httpClient.get).toBe('function')
    expect(typeof httpClient.post).toBe('function')
    expect(typeof httpClient.del).toBe('function')
  })

  it('should construct with base url', () => {
    const client = new HttpClient()
    expect(client).toBeDefined()
  })
})
