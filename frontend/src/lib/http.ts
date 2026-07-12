import { getOrCreateClientId } from './clientId'

interface FetchOptions extends RequestInit {
  headers?: Record<string, string>
}

interface ErrorResponse {
  code: string
  message: string
  seatId: number | null
}

export class HttpClient {
  private baseUrl: string
  private defaultHeaders?: Record<string, string>

  constructor() {
    this.baseUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'
  }

  private getDefaultHeaders(): Record<string, string> {
    if (!this.defaultHeaders) {
      const clientId = getOrCreateClientId()
      this.defaultHeaders = {
        'X-Client-Id': clientId,
        'Content-Type': 'application/json',
      }
    }
    return this.defaultHeaders
  }

  private async request<T>(url: string, options?: FetchOptions): Promise<T> {
    const response = await fetch(url, {
      ...options,
      headers: {
        ...this.getDefaultHeaders(),
        ...options?.headers,
      },
    })

    if (!response.ok) {
      const text = await response.text()
      throw new Error(parseErrorMessage(text) || `HTTP ${response.status}`)
    }

    if (response.status === 204) {
      return undefined as T
    }

    const text = await response.text()
    return text ? (JSON.parse(text) as T) : (undefined as T)
  }

  async get<T>(path: string): Promise<T> {
    return this.request<T>(`${this.baseUrl}${path}`)
  }

  async post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>(`${this.baseUrl}${path}`, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    })
  }

  async del(path: string): Promise<void> {
    await this.request<void>(`${this.baseUrl}${path}`, {
      method: 'DELETE',
    })
  }
}

function parseErrorMessage(text: string): string | undefined {
  if (!text) {
    return undefined
  }
  try {
    const parsed = JSON.parse(text) as ErrorResponse
    return parsed.message
  } catch {
    return text
  }
}

export const http = new HttpClient()
