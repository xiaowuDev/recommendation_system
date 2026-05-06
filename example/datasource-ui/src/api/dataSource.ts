import { request, requestMultipart, requestSse } from './client'
import type {
  DataSourceConnection,
  SupportedType,
  ConnectionTestResult,
  ConnectionTestLog,
  CreateConnectionPayload
} from '@/types/connection'
import type { IngestionJob, MysqlTransformRuleCapability } from '@/types/ingestion'
import type { MysqlAssistantChatResponse, MysqlAssistantHistoryEntry } from '@/types/ai'
import { ApiError } from '@/types/common'

// ---- Connection Management ----

export function fetchSupportedTypes(): Promise<SupportedType[]> {
  return request('/types')
}

export function fetchMysqlTransformRules(): Promise<MysqlTransformRuleCapability[]> {
  return request('/mysql/transform-rules')
}

export function fetchConnections(): Promise<DataSourceConnection[]> {
  return request('/connections')
}

export function fetchConnection(id: number): Promise<DataSourceConnection> {
  return request(`/connections/${id}`)
}

export function createConnection(payload: CreateConnectionPayload): Promise<DataSourceConnection> {
  return request('/connections', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

// ---- Connection Testing ----

export function testTransientConnection(
  type: string,
  config: Record<string, unknown>
): Promise<ConnectionTestResult> {
  return request('/connections/test', {
    method: 'POST',
    body: JSON.stringify({ type, config })
  })
}

export function testSavedConnection(id: number): Promise<ConnectionTestResult> {
  return request(`/connections/${id}/test`, { method: 'POST' })
}

export function fetchTestLogs(id: number, limit = 10): Promise<ConnectionTestLog[]> {
  return request(`/connections/${id}/test-logs?limit=${limit}`)
}

export function testExcel(file: File, params?: {
  sheetName?: string
  headerRowIndex?: number
  sampleSize?: number
}): Promise<ConnectionTestResult> {
  const form = new FormData()
  form.append('file', file)
  if (params?.sheetName) form.append('sheetName', params.sheetName)
  if (params?.headerRowIndex != null) form.append('headerRowIndex', String(params.headerRowIndex))
  if (params?.sampleSize != null) form.append('sampleSize', String(params.sampleSize))
  return requestMultipart('/excel/test', form)
}

// ---- Ingestion Jobs ----

export function triggerIngestion(
  connectionId: number,
  params?: Record<string, unknown>
): Promise<IngestionJob> {
  return request(`/connections/${connectionId}/ingest`, {
    method: 'POST',
    body: JSON.stringify(params ?? {})
  })
}

export function triggerExcelIngestion(file: File, params?: {
  sheetName?: string
  headerRowIndex?: number
  maxRows?: number
}): Promise<IngestionJob> {
  const form = new FormData()
  form.append('file', file)
  if (params?.sheetName) form.append('sheetName', params.sheetName)
  if (params?.headerRowIndex != null) form.append('headerRowIndex', String(params.headerRowIndex))
  if (params?.maxRows != null) form.append('maxRows', String(params.maxRows))
  return requestMultipart('/excel/ingest', form)
}

export function fetchJob(jobId: number): Promise<IngestionJob> {
  return request(`/jobs/${jobId}`)
}

export function fetchConnectionJobs(connectionId: number, limit = 10): Promise<IngestionJob[]> {
  return request(`/connections/${connectionId}/jobs?limit=${limit}`)
}

export function fetchMysqlAssistantHistory(
  connectionId: number,
  limit?: number | null
): Promise<MysqlAssistantHistoryEntry[]> {
  const query = limit != null ? `?limit=${limit}` : ''
  return request(`/connections/${connectionId}/ai/chat/history${query}`)
}

export async function streamMysqlAssistant(
  connectionId: number,
  payload: {
    sessionId?: string | null
    message: string
  },
  handlers: {
    onSession?: (payload: { sessionId: string }) => void
    onDelta?: (payload: { content: string }) => void
    onResult?: (payload: MysqlAssistantChatResponse) => void
    onDone?: () => void
  }
): Promise<void> {
  await requestSse(`/connections/${connectionId}/ai/chat/stream`, {
    method: 'POST',
    body: JSON.stringify(payload),
    async onEvent(event) {
      switch (event.event) {
        case 'session':
          if (event.data) {
            handlers.onSession?.(JSON.parse(event.data) as { sessionId: string })
          }
          return
        case 'delta':
          if (event.data) {
            handlers.onDelta?.(JSON.parse(event.data) as { content: string })
          }
          return
        case 'result':
          if (event.data) {
            handlers.onResult?.(JSON.parse(event.data) as MysqlAssistantChatResponse)
          }
          return
        case 'done':
          handlers.onDone?.()
          return
        case 'error': {
          const payload = event.data
            ? JSON.parse(event.data) as { status?: number; message?: string }
            : {}
          throw new ApiError(payload.status ?? 500, payload.message || 'SSE request failed')
        }
        default:
          return
      }
    }
  })
}
