<script setup lang="ts">
import { useLocale } from '@/composables/useLocale'

defineProps<{
  columns: string[]
  rows: Record<string, unknown>[]
}>()

const { t } = useLocale()

function formatCell(value: unknown): string {
  if (value == null) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
</script>

<template>
  <div class="table-wrapper">
    <table class="data-table">
      <thead>
        <tr>
          <th v-for="col in columns" :key="col">{{ col }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, idx) in rows" :key="idx">
          <td v-for="col in columns" :key="col" :title="formatCell(row[col])">
            {{ formatCell(row[col]) }}
          </td>
        </tr>
        <tr v-if="rows.length === 0">
          <td :colspan="columns.length" class="empty-row">{{ t('table.empty') }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.table-wrapper {
  overflow-x: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}
.empty-row {
  text-align: center;
  color: var(--color-text-muted);
  padding: 24px !important;
}
</style>
