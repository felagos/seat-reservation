import '@testing-library/jest-dom'
import { vi } from 'vitest'

global.ResizeObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}))

// Node's native `localStorage` global (added in recent Node versions) shadows jsdom's
// per-window Storage implementation with a broken empty stub. Replace it with a minimal
// in-memory polyfill so getOrCreateClientId() and friends work under test.
function createLocalStoragePolyfill(): Storage {
  const store = new Map<string, string>()
  return {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => void store.set(key, String(value)),
    removeItem: (key: string) => void store.delete(key),
    clear: () => store.clear(),
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    get length() {
      return store.size
    },
  } as Storage
}

Object.defineProperty(window, 'localStorage', { value: createLocalStoragePolyfill(), configurable: true })
