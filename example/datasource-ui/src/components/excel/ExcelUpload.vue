<script setup lang="ts">
import { ref } from 'vue'
import { useIngestionJob } from '@/composables/useIngestionJob'
import { useLocale } from '@/composables/useLocale'
import StatusBadge from '@/components/shared/StatusBadge.vue'
import DataTable from '@/components/shared/DataTable.vue'

const fileInput = ref<HTMLInputElement>()
const selectedFile = ref<File | null>(null)
const sheetName = ref('')
const headerRowIndex = ref(0)
const maxRows = ref(1000)

const { currentJob, executing, jobError, executeExcel } = useIngestionJob()
const { t } = useLocale()

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
}

async function handleIngest() {
  if (!selectedFile.value) return
  await executeExcel(selectedFile.value, {
    sheetName: sheetName.value || undefined,
    headerRowIndex: headerRowIndex.value,
    maxRows: maxRows.value
  })
}
</script>

<template>
  <div class="excel-upload">
    <div class="intro-card">
      <div>
        <h3>{{ t('excel.title') }}</h3>
        <p>{{ t('excel.description') }}</p>
      </div>
      <span class="pill">{{ t('excel.fileOnly') }}</span>
    </div>

    <div class="form-row">
      <label class="label">{{ t('excel.selectFile') }}</label>
      <input ref="fileInput" type="file" accept=".xlsx" class="input" @change="onFileChange" />
    </div>

    <div class="param-row">
      <div class="field-shell">
        <label class="label">{{ t('excel.sheetName') }}</label>
        <input v-model="sheetName" class="input" :placeholder="t('excel.firstSheet')" />
      </div>
      <div class="field-shell">
        <label class="label">{{ t('excel.headerRow') }}</label>
        <input v-model.number="headerRowIndex" class="input" type="number" min="0" />
      </div>
      <div class="field-shell">
        <label class="label">{{ t('excel.maxRows') }}</label>
        <input v-model.number="maxRows" class="input" type="number" />
      </div>
    </div>

    <div class="action-row">
      <button
        class="btn btn-primary"
        :disabled="!selectedFile || executing"
        @click="handleIngest"
      >
        {{ executing ? t('excel.processing') : t('excel.import') }}
      </button>
      <span class="action-copy">
        {{ selectedFile ? selectedFile.name : t('excel.noFile') }}
      </span>
    </div>

    <div v-if="jobError" class="upload-error">{{ jobError }}</div>

    <div v-if="currentJob" class="upload-result">
      <div class="result-summary">
        <StatusBadge :status="currentJob.status" />
        <span v-if="currentJob.totalRecords != null">{{ t('result.records', { count: currentJob.totalRecords }) }}</span>
        <span v-if="currentJob.message" class="result-msg">{{ currentJob.message }}</span>
      </div>
      <DataTable
        v-if="currentJob.columns && currentJob.sampleRows && currentJob.sampleRows.length > 0"
        :columns="currentJob.columns"
        :rows="currentJob.sampleRows"
      />
    </div>
  </div>
</template>

<style scoped>
.excel-upload {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 760px;
}

.intro-card {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px;
  border-radius: var(--radius-lg);
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
}

.intro-card h3 {
  font-size: 20px;
  letter-spacing: -0.03em;
  margin-bottom: 4px;
}

.intro-card p {
  color: var(--color-text-secondary);
}

.form-row { margin-bottom: 12px; }
.param-row { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; margin-bottom: 12px; }

.action-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.action-copy {
  color: var(--color-text-muted);
  font-size: 12px;
}

.upload-error {
  margin-top: 8px;
  padding: 14px 16px;
  background: rgba(255,123,123,.08);
  border-radius: var(--radius-md);
  border: 1px solid rgba(255,123,123,.2);
  color: #ffd7d7;
  font-size: 13px;
}
.upload-result {
  margin-top: 4px;
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
  font-size: 14px;
}
.result-msg { font-size: 13px; color: var(--color-text-secondary); }

@media (max-width: 640px) {
  .intro-card,
  .action-row,
  .param-row {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
