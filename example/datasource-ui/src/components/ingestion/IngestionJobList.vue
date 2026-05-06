<script setup lang="ts">
import { useLocale } from '@/composables/useLocale'
import type { IngestionJob } from '@/types/ingestion'

defineProps<{ jobs: IngestionJob[] }>()
const { t, localeCode, isZh } = useLocale()

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
</script>

<template>
  <el-table :data="jobs" size="small" :empty-text="t('jobs.empty')">
    <el-table-column prop="id" :label="isZh ? '任务 ID' : 'Job ID'" width="90">
      <template #default="{ row }">#{{ row.id }}</template>
    </el-table-column>

    <el-table-column prop="dataSourceType" :label="isZh ? '类型' : 'Type'" width="120" />

    <el-table-column :label="isZh ? '状态' : 'Status'" width="140">
      <template #default="{ row }">
        <el-tag :type="statusTagType(row.status)" effect="dark">{{ t(`status.${row.status}`) }}</el-tag>
      </template>
    </el-table-column>

    <el-table-column :label="isZh ? '记录数' : 'Records'" min-width="140">
      <template #default="{ row }">
        {{ row.totalRecords != null ? t('result.records', { count: row.totalRecords }) : '-' }}
      </template>
    </el-table-column>

    <el-table-column :label="isZh ? '耗时' : 'Duration'" width="120">
      <template #default="{ row }">{{ durationStr(row) }}</template>
    </el-table-column>

    <el-table-column :label="isZh ? '开始时间' : 'Started At'" min-width="220">
      <template #default="{ row }">
        {{ new Date(row.startedAt).toLocaleString(localeCode) }}
      </template>
    </el-table-column>
  </el-table>
</template>
