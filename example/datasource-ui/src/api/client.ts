import { ApiError } from '@/types/common'

const BASE_URL = '/api/examples/data-sources'

type SseEvent = {
  event: string
  data: string
}

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

export async function requestSse(
  path: string,
  options: RequestInit & {
    onEvent: (event: SseEvent) => void | Promise<void>
  }
): Promise<void> {
  const url = `${BASE_URL}${path}`
  const { onEvent, ...requestOptions } = options
  const response = await fetch(url, {
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...requestOptions.headers
    },
    ...requestOptions
  })

  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: response.statusText }))
    throw new ApiError(response.status, body.message || response.statusText)
  }

  if (!response.body) {
    throw new ApiError(response.status, 'SSE response body is empty')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done }).replace(/\r/g, '')

    let separatorIndex = buffer.indexOf('\n\n')
    while (separatorIndex >= 0) {
      const rawEvent = buffer.slice(0, separatorIndex)
      buffer = buffer.slice(separatorIndex + 2)
      if (rawEvent.trim()) {
        await onEvent(parseSseEvent(rawEvent))
      }
      separatorIndex = buffer.indexOf('\n\n')
    }

    if (done) {
      break
    }
  }

  if (buffer.trim()) {
    await onEvent(parseSseEvent(buffer))
  }
}

function parseSseEvent(rawEvent: string): SseEvent {
  let eventName = 'message'
  const dataLines: string[] = []

  for (const line of rawEvent.split('\n')) {
    if (!line || line.startsWith(':')) {
      continue
    }

    const separatorIndex = line.indexOf(':')
    const field = separatorIndex >= 0 ? line.slice(0, separatorIndex) : line
    const value = separatorIndex >= 0 ? line.slice(separatorIndex + 1).trimStart() : ''

    if (field === 'event') {
      eventName = value
      continue
    }

    if (field === 'data') {
      dataLines.push(value)
    }
  }

  return {
    event: eventName,
    data: dataLines.join('\n')
  }
}
