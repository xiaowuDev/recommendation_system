export type MysqlFilterOperator =
  | 'EQ'
  | 'NE'
  | 'GT'
  | 'GTE'
  | 'LT'
  | 'LTE'
  | 'LIKE'
  | 'IN'
  | 'IS_NULL'
  | 'NOT_NULL'

export type MysqlTargetWriteMode = 'APPEND' | 'UPSERT' | 'REPLACE'

export type MysqlBuilderTabId = 'source' | 'mapping' | 'transform' | 'target'

export type MysqlFieldMappingRule = {
  sourceField: string
  targetField: string
  enabled: boolean
}

export type MysqlFilterRule = {
  logic: 'AND' | 'OR'
  field: string
  operator: MysqlFilterOperator
  value: string
  enabled: boolean
}

export type MysqlTransformRule = {
  field: string
  targetField: string
  transformType: string
  argument: string
  enabled: boolean
}

export type MysqlTargetConfig = {
  targetName: string
  writeMode: MysqlTargetWriteMode
  primaryKey: string
  incrementalField: string
}

export type MysqlSourceConfig = {
  useCustomQuery: boolean
  customQuery: string
  sourceTable: string
  sortField: string
  sortDirection: 'ASC' | 'DESC'
  maxRows: number
}

export type MysqlBuilderTab = {
  id: MysqlBuilderTabId
  label: string
  meta: string
  status?: 'todo' | 'active' | 'done'
}

export type MysqlBuilderSummaryItem = {
  label: string
  value: string
  tone?: 'default' | 'accent' | 'success'
}
