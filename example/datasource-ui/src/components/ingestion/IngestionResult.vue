<script setup lang="ts">
import { computed } from 'vue'
import JsonViewer from '@/components/shared/JsonViewer.vue'
import { useLocale } from '@/composables/useLocale'
import type { IngestionJob } from '@/types/ingestion'

const props = defineProps<{ job: IngestionJob }>()
const { t, isZh } = useLocale()

function durationStr(job: IngestionJob): string {
  if (!job.startedAt || !job.finishedAt) return '-'
  const ms = new Date(job.finishedAt).getTime() - new Date(job.startedAt).getTime()
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
}

function statusTagType(status: string) {
  switch (status.toUpperCase()) {
    case 'COMPLETED':
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
    case 'BLOCKED':
      return 'danger'
    case 'RUNNING':
    case 'PENDING':
      return 'warning'
    default:
      return 'info'
  }
}

function formatCell(value: unknown): string {
  if (value == null) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

const previewColumns = computed(() => props.job.columns ?? [])
const previewRows = computed(() => props.job.sampleRows ?? [])
</script>

<template>
  <el-card shadow="never" class="result-card">
    <template #header>
      <div class="result-head">
        <div class="result-title">
          <h3>{{ isZh ? '执行结果' : 'Execution Result' }}</h3>
          <p class="result-copy">
            {{ isZh ? '本次任务的状态、样例数据和执行元数据都在这里集中查看。' : 'Status, sample rows, and execution metadata are all surfaced here.' }}
          </p>
        </div>

        <div class="result-summary">
          <el-tag :type="statusTagType(job.status)" effect="dark">{{ t(`status.${job.status}`) }}</el-tag>
          <el-tag v-if="job.totalRecords != null" type="info" effect="plain">
            {{ t('result.records', { count: job.totalRecords }) }}
          </el-tag>
          <el-tag type="warning" effect="plain">{{ durationStr(job) }}</el-tag>
        </div>
      </div>
    </template>

    <el-alert
      v-if="job.message"
      :title="job.message"
      type="info"
      :closable="false"
      show-icon
      class="result-alert"
    />

    <el-descriptions :column="3" border class="result-descriptions">
      <el-descriptions-item :label="isZh ? '任务 ID' : 'Job ID'">#{{ job.id }}</el-descriptions-item>
      <el-descriptions-item :label="isZh ? '数据源类型' : 'Source Type'">{{ job.dataSourceType }}</el-descriptions-item>
      <el-descriptions-item :label="isZh ? '开始时间' : 'Started At'">{{ new Date(job.startedAt).toLocaleString() }}</el-descriptions-item>
    </el-descriptions>

    <div v-if="job.metadata && Object.keys(job.metadata).length" class="meta-block">
      <div class="meta-head">{{ isZh ? '执行元数据' : 'Execution Metadata' }}</div>
      <JsonViewer :data="job.metadata" />
    </div>

    <div v-if="previewColumns.length && previewRows.length" class="table-block">
      <div class="meta-head">{{ isZh ? '样例数据' : 'Sample Rows' }}</div>
      <el-table :data="previewRows" size="small" max-height="360">
        <el-table-column
          v-for="column in previewColumns"
          :key="column"
          :prop="column"
          :label="column"
          min-width="160"
        >
          <template #default="{ row }">
            <span class="result-cell" :title="formatCell(row[column])">
              {{ formatCell(row[column]) }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-card>
</template>

<style scoped>
.result-card h3 {
  margin: 0 0 6px;
  font-size: 22px;
  font-family: var(--font-display);
  letter-spacing: -0.03em;
}

.result-head,
.result-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.result-summary {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.result-copy {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.7;
}

.result-alert,
.result-descriptions,
.meta-block,
.table-block {
  margin-top: 16px;
}

.meta-head {
  margin-bottom: 10px;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.result-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .result-head {
    flex-direction: column;
  }
}
</style>
