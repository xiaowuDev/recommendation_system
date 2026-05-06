export type IngestionJobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface IngestionJob {
  id: number
  connectionId: number | null
  dataSourceType: string
  status: IngestionJobStatus
  message: string | null
  totalRecords: number | null
  columns: string[] | null
  sampleRows: Record<string, unknown>[] | null
  metadata: Record<string, unknown> | null
  startedAt: string
  finishedAt: string | null
}

export interface MysqlTransformRuleCapability {
  code: string
  displayName: string
  description: string
  argumentMode: 'NONE' | 'OPTIONAL' | 'REQUIRED'
  argumentHint: string | null
}
