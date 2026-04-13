<script setup lang="ts">
import type { IngestionJob } from '@/types/ingestion'
import { useLocale } from '@/composables/useLocale'
import StatusBadge from '@/components/shared/StatusBadge.vue'
import DataTable from '@/components/shared/DataTable.vue'

defineProps<{ job: IngestionJob }>()
const { t } = useLocale()

function durationStr(job: IngestionJob): string {
  if (!job.startedAt || !job.finishedAt) return '-'
  const ms = new Date(job.finishedAt).getTime() - new Date(job.startedAt).getTime()
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
}
</script>

<template>
  <div class="result-panel">
    <div class="result-summary">
      <StatusBadge :status="job.status" />
      <span class="result-records" v-if="job.totalRecords != null">{{ t('result.records', { count: job.totalRecords }) }}</span>
      <span class="result-duration">{{ durationStr(job) }}</span>
    </div>

    <div v-if="job.message" class="result-message">{{ job.message }}</div>

    <DataTable
      v-if="job.columns && job.sampleRows && job.sampleRows.length > 0"
      :columns="job.columns"
      :rows="job.sampleRows"
    />
  </div>
</template>

<style scoped>
.result-panel {
  margin-top: 18px;
  padding: 18px;
  border-radius: var(--radius-lg);
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
}
.result-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.result-records { font-size: 14px; font-weight: 700; }
.result-duration { font-size: 12px; color: var(--color-text-muted); }
.result-message { font-size: 13px; color: var(--color-text-secondary); margin-bottom: 14px; }
</style>
