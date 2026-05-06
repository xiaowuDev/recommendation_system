export interface MysqlAssistantFieldMappingRule {
  sourceField: string
  targetField: string
  enabled?: boolean | null
}

export interface MysqlAssistantFilterRule {
  logic?: 'AND' | 'OR' | null
  field: string
  operator: 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'LIKE' | 'IN' | 'IS_NULL' | 'NOT_NULL'
  value?: string | null
  enabled?: boolean | null
}

export interface MysqlAssistantTransformRule {
  field: string
  targetField?: string | null
  transformType: string
  argument?: string | null
  enabled?: boolean | null
}

export interface MysqlAssistantTargetConfig {
  targetName?: string | null
  writeMode?: 'APPEND' | 'UPSERT' | 'REPLACE' | null
  primaryKey?: string | null
  incrementalField?: string | null
}

export interface MysqlAssistantSuggestedRequest {
  query?: string | null
  sourceTable?: string | null
  fieldMappings?: MysqlAssistantFieldMappingRule[] | null
  filters?: MysqlAssistantFilterRule[] | null
  transformRules?: MysqlAssistantTransformRule[] | null
  target?: MysqlAssistantTargetConfig | null
  sortField?: string | null
  sortDirection?: 'ASC' | 'DESC' | null
  maxRows?: number | null
}

export interface MysqlAssistantChatResponse {
  sessionId: string
  assistantMessage: string
  sourceSummary: string | null
  suggestionAvailable: boolean
  suggestedRequest: MysqlAssistantSuggestedRequest | null
  warnings: string[]
  generatedAt: string
}

export interface MysqlAssistantHistoryEntry {
  id: number
  connectionId: number
  sessionId: string
  userMessage: string
  assistantMessage: string
  sourceSummary: string | null
  suggestionAvailable: boolean
  suggestedRequest: MysqlAssistantSuggestedRequest | null
  warnings: string[]
  generatedAt: string
  createdAt: string
}
