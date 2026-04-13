import { ApiError } from '@/types/common'

const BASE_URL = '/api/examples/data-sources'

export async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const url = `${BASE_URL}${path}`
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers
    },
    ...options
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: response.statusText }))
    throw new ApiError(response.status, body.message || response.statusText)
  }
  return response.json()
}

export async function requestMultipart<T>(path: string, formData: FormData): Promise<T> {
  const url = `${BASE_URL}${path}`
  const response = await fetch(url, {
    method: 'POST',
    body: formData
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: response.statusText }))
    throw new ApiError(response.status, body.message || response.statusText)
  }
  return response.json()
}
