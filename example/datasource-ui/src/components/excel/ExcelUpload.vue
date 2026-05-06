<script setup lang="ts">
import { UploadFilled } from '@element-plus/icons-vue'
import { ref } from 'vue'
import type { UploadFile, UploadProps } from 'element-plus'
import IngestionResult from '@/components/ingestion/IngestionResult.vue'
import { useIngestionJob } from '@/composables/useIngestionJob'
import { useLocale } from '@/composables/useLocale'

const selectedFile = ref<File | null>(null)
const sheetName = ref('')
const headerRowIndex = ref(0)
const maxRows = ref(1000)

const { currentJob, executing, jobError, executeExcel } = useIngestionJob()
const { t, isZh } = useLocale()

const preventUpload: UploadProps['beforeUpload'] = () => false

function handleFileChange(uploadFile: UploadFile) {
  selectedFile.value = uploadFile.raw ?? null
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
  <el-card shadow="never" class="excel-card">
    <template #header>
      <div class="excel-head">
        <div>
          <h3>{{ t('excel.title') }}</h3>
          <p>{{ t('excel.description') }}</p>
        </div>
        <el-tag type="success" effect="dark">{{ t('excel.fileOnly') }}</el-tag>
      </div>
    </template>

    <el-upload
      drag
      :auto-upload="false"
      :show-file-list="false"
      accept=".xlsx"
      :before-upload="preventUpload"
      :on-change="handleFileChange"
      class="excel-upload"
    >
      <el-icon class="excel-upload-icon"><UploadFilled /></el-icon>
      <div class="el-upload__text">
        {{ t('excel.selectFile') }}
      </div>
      <template #tip>
        <div class="el-upload__tip">
          {{ selectedFile ? selectedFile.name : t('excel.noFile') }}
        </div>
      </template>
    </el-upload>

    <el-form label-position="top" class="excel-form">
      <el-row :gutter="16">
        <el-col :xs="24" :md="10">
          <el-form-item :label="t('excel.sheetName')">
            <el-input v-model="sheetName" :placeholder="t('excel.firstSheet')" />
          </el-form-item>
        </el-col>

        <el-col :xs="24" :md="7">
          <el-form-item :label="t('excel.headerRow')">
            <el-input-number
              v-model="headerRowIndex"
              :min="0"
              controls-position="right"
              class="full-width"
            />
          </el-form-item>
        </el-col>

        <el-col :xs="24" :md="7">
          <el-form-item :label="t('excel.maxRows')">
            <el-input-number
              v-model="maxRows"
              :min="1"
              controls-position="right"
              class="full-width"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <div class="excel-actions">
      <el-button
        type="primary"
        size="large"
        :loading="executing"
        :disabled="!selectedFile"
        @click="handleIngest"
      >
        {{ executing ? t('excel.processing') : t('excel.import') }}
      </el-button>
      <el-text>{{ selectedFile ? selectedFile.name : t('excel.noFile') }}</el-text>
    </div>

    <el-alert
      v-if="jobError"
      :title="jobError"
      type="error"
      :closable="false"
      show-icon
      class="excel-error"
    />

    <IngestionResult v-if="currentJob" :job="currentJob" />
  </el-card>
</template>

<style scoped>
.excel-card h3 {
  margin: 0 0 6px;
  font-size: 22px;
  letter-spacing: -0.03em;
}

.excel-head,
.excel-actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.excel-head p {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.7;
}

.excel-form,
.excel-actions,
.excel-error {
  margin-top: 18px;
}

.excel-upload-icon {
  font-size: 28px;
  margin-bottom: 8px;
}

.full-width {
  width: 100%;
}

@media (max-width: 720px) {
  .excel-head,
  .excel-actions {
    flex-direction: column;
  }

  .excel-actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
