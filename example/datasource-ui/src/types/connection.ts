export type DataSourceType = 'KAFKA' | 'MYSQL' | 'REDIS' | 'EXCEL'

export interface DataSourceConnection {
  id: number
  connectionName: string
  description: string | null
  type: string
  maskedConfig: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface SupportedType {
  type: DataSourceType
  description: string
  requiredFields: string[]
  optionalFields: string[]
}

export interface ConnectionTestResult {
  success: boolean
  message: string
  detail: Record<string, unknown>
  testedAt: string
}

export interface ConnectionTestLog {
  id: number
  connectionId: number
  dataSourceType: string
  success: boolean
  message: string
  detail: Record<string, unknown>
  testedAt: string
}

export interface CreateConnectionPayload {
  connectionName: string
  description?: string
  type: DataSourceType
  config: Record<string, unknown>
}
