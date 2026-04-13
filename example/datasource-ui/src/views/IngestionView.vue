<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useConnections } from '@/composables/useConnections'
import { useIngestionJob } from '@/composables/useIngestionJob'
import { useLocale } from '@/composables/useLocale'
import type { DataSourceConnection } from '@/types/connection'
import { fetchConnection } from '@/api/dataSource'
import IngestionTrigger from '@/components/ingestion/IngestionTrigger.vue'
import IngestionResult from '@/components/ingestion/IngestionResult.vue'
import IngestionJobList from '@/components/ingestion/IngestionJobList.vue'
import ExcelUpload from '@/components/excel/ExcelUpload.vue'

const { connections, loadConnections } = useConnections()
const { currentJob, recentJobs, loadRecentJobs } = useIngestionJob()
const { t } = useLocale()

const selectedId = ref<number | null>(null)
const selectedConn = ref<DataSourceConnection | null>(null)
const showExcel = ref(false)

const nonExcelConnections = computed(() => connections.value.filter(conn => conn.type !== 'EXCEL'))

onMounted(() => loadConnections())

watch(selectedId, async (id) => {
  if (id == null) {
    selectedConn.value = null
    return
  }

  try {
    selectedConn.value = await fetchConnection(id)
    loadRecentJobs(id)
  } catch {
    selectedConn.value = null
  }
})
</script>

<template>
  <div class="page-shell ingestion-page">
    <section class="hero-panel">
      <div class="hero-top">
        <div class="hero-copy">
          <span class="section-eyebrow">{{ t('ingestion.eyebrow') }}</span>
          <h1 class="page-title hero-title">{{ t('ingestion.title') }}</h1>
          <p class="hero-description">{{ t('ingestion.description') }}</p>
        </div>

        <div class="hero-actions">
          <button class="btn" :class="{ 'btn-primary': !showExcel }" @click="showExcel = false">{{ t('ingestion.tab.connections') }}</button>
          <button class="btn" :class="{ 'btn-primary': showExcel }" @click="showExcel = true">{{ t('ingestion.tab.excel') }}</button>
        </div>
      </div>
    </section>

    <section class="surface-panel">
      <div class="panel-body content-body">
        <template v-if="showExcel">
          <div class="section-heading">
            <div>
              <h2 class="section-title">{{ t('ingestion.spreadsheetTitle') }}</h2>
              <p class="muted-copy">{{ t('ingestion.spreadsheetDesc') }}</p>
            </div>
            <span class="pill">{{ t('ingestion.fileFlow') }}</span>
          </div>
          <ExcelUpload />
        </template>

        <template v-else>
          <div class="section-heading">
            <div>
              <h2 class="section-title">{{ t('ingestion.connectionRunsTitle') }}</h2>
              <p class="muted-copy">{{ t('ingestion.connectionRunsDesc') }}</p>
            </div>
            <span class="pill">{{ t('ingestion.available', { count: nonExcelConnections.length }) }}</span>
          </div>

          <div class="picker-shell">
            <label class="label">{{ t('ingestion.selectConnection') }}</label>
            <select v-model="selectedId" class="select picker-select">
              <option :value="null">{{ t('ingestion.chooseConnection') }}</option>
              <option
                v-for="conn in nonExcelConnections"
                :key="conn.id"
                :value="conn.id"
              >
                [{{ conn.type }}] {{ conn.connectionName }}
              </option>
            </select>
          </div>

          <div v-if="selectedConn" class="ingestion-grid">
            <section class="ingestion-panel">
              <div class="panel-head">
                <h3>{{ t('ingestion.triggerRun') }}</h3>
                <span class="pill">{{ selectedConn.type }}</span>
              </div>
              <p class="panel-copy">{{ t('ingestion.tuneExecution', { name: selectedConn.connectionName }) }}</p>
              <IngestionTrigger :connection-id="selectedConn.id" :type="selectedConn.type" />
              <IngestionResult v-if="currentJob" :job="currentJob" />
            </section>

            <section class="ingestion-panel">
              <div class="panel-head">
                <h3>{{ t('ingestion.recentJobs') }}</h3>
                <span class="pill">{{ t('ingestion.runs', { count: recentJobs.length }) }}</span>
              </div>
              <IngestionJobList :jobs="recentJobs" />
            </section>
          </div>

          <div v-else class="empty-panel">
            {{ t('ingestion.empty') }}
          </div>
        </template>
      </div>
    </section>
  </div>
</template>

<style scoped>
.ingestion-page {
  padding-top: 4px;
}

.hero-top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 18px;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-actions {
  display: flex;
  gap: 10px;
}

.content-body {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.picker-shell {
  max-width: 420px;
}

.picker-select {
  min-width: 280px;
}

.ingestion-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(320px, 0.92fr);
  gap: 18px;
}

.ingestion-panel {
  padding: 22px;
  border-radius: var(--radius-lg);
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.panel-head h3 {
  font-size: 18px;
  letter-spacing: -0.03em;
}

.panel-copy {
  color: var(--color-text-secondary);
  margin-bottom: 16px;
}

@media (max-width: 980px) {
  .ingestion-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .hero-top,
  .panel-head,
  .hero-actions {
    flex-direction: column;
    align-items: start;
  }

  .picker-shell,
  .picker-select {
    max-width: none;
    min-width: 0;
    width: 100%;
  }
}
</style>
