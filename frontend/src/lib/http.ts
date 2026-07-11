import { getOrCreateClientId } from './clientId'

interface FetchOptions extends RequestInit {
  headers?: Record<string, string>
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
      throw new Error(text || `HTTP ${response.status}`)
    }

    return response.json() as Promise<T>
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

  async del<T>(path: string): Promise<T | void> {
    try {
      return await this.request<T>(`${this.baseUrl}${path}`, {
        method: 'DELETE',
      })
    } catch (error) {
      if (error instanceof Error && error.message === 'HTTP 204') {
        return
      }
      throw error
    }
  }
}

export const http = new HttpClient()
