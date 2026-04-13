<script setup lang="ts">
import { ref, watch } from 'vue'
import type { DataSourceConnection, ConnectionTestLog } from '@/types/connection'
import { useLocale } from '@/composables/useLocale'
import { fetchTestLogs } from '@/api/dataSource'
import { useConnectionTest } from '@/composables/useConnectionTest'
import { useIngestionJob } from '@/composables/useIngestionJob'
import StatusBadge from '@/components/shared/StatusBadge.vue'
import JsonViewer from '@/components/shared/JsonViewer.vue'
import PretextRenderer from '@/components/shared/PretextRenderer.vue'
import LoadingSpinner from '@/components/shared/LoadingSpinner.vue'
import IngestionTrigger from '@/components/ingestion/IngestionTrigger.vue'
import IngestionResult from '@/components/ingestion/IngestionResult.vue'

const props = defineProps<{ connection: DataSourceConnection }>()
const { t, localeCode } = useLocale()

const { testResult, testing, testError, testSaved } = useConnectionTest()
const { currentJob } = useIngestionJob()
const testLogs = ref<ConnectionTestLog[]>([])

async function loadLogs() {
  try {
    testLogs.value = await fetchTestLogs(props.connection.id)
  } catch {
    testLogs.value = []
  }
}

watch(() => props.connection.id, () => {
  testResult.value = null
  currentJob.value = null
  loadLogs()
}, { immediate: true })

async function handleTest() {
  await testSaved(props.connection.id)
  loadLogs()
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString(localeCode.value)
}
</script>

<template>
  <div class="detail">
    <section class="detail-hero">
      <div class="detail-hero-copy">
        <span class="section-eyebrow">{{ t('detail.eyebrow') }}</span>
        <div class="detail-headline">
          <h2 class="detail-name">{{ connection.connectionName }}</h2>
          <span class="detail-type">{{ connection.type }}</span>
        </div>
        <p class="detail-meta">
          {{ t('detail.meta', { created: formatDate(connection.createdAt), updated: formatDate(connection.updatedAt) }) }}
        </p>
      </div>

      <div class="detail-actions">
        <button class="btn btn-primary" :disabled="testing" @click="handleTest">
          {{ testing ? t('detail.testing') : t('detail.test') }}
        </button>
      </div>
    </section>

    <PretextRenderer v-if="connection.description" :source="connection.description" />

    <div class="detail-grid">
      <section class="detail-panel">
        <div class="panel-head">
          <h4>{{ t('detail.maskedConfig') }}</h4>
          <span class="pill">{{ t('detail.securePreview') }}</span>
        </div>
        <JsonViewer :data="connection.maskedConfig" />
      </section>

      <section class="detail-panel">
        <div class="panel-head">
          <h4>{{ t('detail.validation') }}</h4>
          <span class="pill">{{ t('detail.immediateFeedback') }}</span>
        </div>

        <LoadingSpinner v-if="testing" :text="t('detail.runningChecks')" />
        <div v-else-if="testResult" class="test-result" :class="testResult.success ? 'result-ok' : 'result-fail'">
          <StatusBadge :status="testResult.success ? 'SUCCESS' : 'FAILED'" />
          <div>
            <strong>{{ testResult.message }}</strong>
            <p>{{ formatDate(testResult.testedAt) }}</p>
          </div>
        </div>
        <div v-else class="result-idle">
          {{ t('detail.idleValidation') }}
        </div>

        <div v-if="testError" class="test-result result-fail">{{ testError }}</div>
      </section>
    </div>

    <section class="detail-panel" v-if="connection.type !== 'EXCEL'">
      <div class="panel-head">
        <h4>{{ t('detail.ingestionControls') }}</h4>
        <span class="pill">{{ t('detail.runOnDemand') }}</span>
      </div>
      <IngestionTrigger :connection-id="connection.id" :type="connection.type" />
      <IngestionResult v-if="currentJob" :job="currentJob" />
    </section>

    <section class="detail-panel" v-if="testLogs.length">
      <div class="panel-head">
        <h4>{{ t('detail.validationHistory') }}</h4>
        <span class="pill">{{ t('detail.recentChecks', { count: testLogs.length }) }}</span>
      </div>
      <div class="log-list">
        <article v-for="log in testLogs" :key="log.id" class="log-item">
          <StatusBadge :status="log.success ? 'SUCCESS' : 'FAILED'" />
          <div class="log-copy">
            <strong>{{ log.message }}</strong>
            <span>{{ log.dataSourceType }} / {{ formatDate(log.testedAt) }}</span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.detail-hero {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 8px;
}

.detail-hero-copy {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail-headline {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.detail-name {
  font-family: var(--font-display);
  font-size: 40px;
  line-height: 1;
  letter-spacing: -0.05em;
}

.detail-type {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
  color: var(--color-primary-hover);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.detail-meta {
  color: var(--color-text-secondary);
  font-size: 14px;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(300px, 0.92fr);
  gap: 18px;
}

.detail-panel {
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
  margin-bottom: 16px;
}

.panel-head h4 {
  font-size: 18px;
  letter-spacing: -0.03em;
}

.test-result,
.result-idle {
  display: flex;
  align-items: start;
  gap: 12px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 13px;
}

.test-result strong,
.log-copy strong {
  display: block;
  margin-bottom: 2px;
}

.test-result p,
.log-copy span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.result-idle {
  color: var(--color-text-secondary);
  background: rgba(255, 255, 255, 0.03);
}

.result-ok {
  background: rgba(95, 208, 157, 0.1);
  border-color: rgba(95, 208, 157, 0.2);
}

.result-fail {
  background: rgba(255, 123, 123, 0.08);
  border-color: rgba(255, 123, 123, 0.2);
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.log-item {
  display: flex;
  align-items: start;
  gap: 12px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(255, 255, 255, 0.03);
}

.log-copy {
  flex: 1;
}

@media (max-width: 980px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .detail-hero,
  .panel-head {
    flex-direction: column;
    align-items: start;
  }

  .detail-name {
    font-size: 30px;
  }

  .detail-panel {
    padding: 18px;
  }
}
</style>
