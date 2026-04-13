<script setup lang="ts">
import type { IngestionJob } from '@/types/ingestion'
import { useLocale } from '@/composables/useLocale'
import StatusBadge from '@/components/shared/StatusBadge.vue'

defineProps<{ jobs: IngestionJob[] }>()
const { t, localeCode } = useLocale()

function durationStr(job: IngestionJob): string {
  if (!job.startedAt || !job.finishedAt) return '-'
  const ms = new Date(job.finishedAt).getTime() - new Date(job.startedAt).getTime()
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
}
</script>

<template>
  <div class="job-list">
    <div v-for="job in jobs" :key="job.id" class="job-item">
      <span class="job-id">#{{ job.id }}</span>
      <span class="job-type">{{ job.dataSourceType }}</span>
      <StatusBadge :status="job.status" />
      <span class="job-records">{{ job.totalRecords != null ? t('result.records', { count: job.totalRecords }) : '-' }}</span>
      <span class="job-duration">{{ durationStr(job) }}</span>
      <span class="job-time">{{ new Date(job.startedAt).toLocaleString(localeCode) }}</span>
    </div>
    <div v-if="jobs.length === 0" class="empty">{{ t('jobs.empty') }}</div>
  </div>
</template>

<style scoped>
.job-list { display: flex; flex-direction: column; gap: 10px; }
.job-item {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  font-size: 13px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
}
.job-id { font-weight: 700; color: var(--color-text-muted); min-width: 44px; }
.job-type {
  font-size: 11px;
  font-weight: 700;
  min-width: 52px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.job-records { flex: 1; }
.job-duration { color: var(--color-text-muted); font-size: 12px; }
.job-time { font-size: 11px; color: var(--color-text-muted); }
.empty { text-align: center; color: var(--color-text-muted); padding: 20px; font-size: 13px; }
</style>
