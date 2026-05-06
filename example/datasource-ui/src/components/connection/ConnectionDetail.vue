<script setup lang="ts">
import { ref, watch } from "vue";
import type {
  DataSourceConnection,
  ConnectionTestLog,
} from "@/types/connection";
import { useLocale } from "@/composables/useLocale";
import { fetchTestLogs } from "@/api/dataSource";
import { useConnectionTest } from "@/composables/useConnectionTest";
import { useIngestionJob } from "@/composables/useIngestionJob";
import StatusBadge from "@/components/shared/StatusBadge.vue";
import JsonViewer from "@/components/shared/JsonViewer.vue";
import PretextRenderer from "@/components/shared/PretextRenderer.vue";
import LoadingSpinner from "@/components/shared/LoadingSpinner.vue";
import IngestionTrigger from "@/components/ingestion/IngestionTrigger.vue";
import IngestionResult from "@/components/ingestion/IngestionResult.vue";

const props = defineProps<{ connection: DataSourceConnection }>();
const { t, localeCode } = useLocale();

const activeTab = ref("overview");
const { testResult, testing, testError, testSaved } = useConnectionTest();
const { currentJob } = useIngestionJob();
const testLogs = ref<ConnectionTestLog[]>([]);

async function loadLogs() {
  try {
    testLogs.value = await fetchTestLogs(props.connection.id);
  } catch {
    testLogs.value = [];
  }
}

watch(
  () => props.connection.id,
  () => {
    testResult.value = null;
    currentJob.value = null;
    activeTab.value = "overview";
    loadLogs();
  },
  { immediate: true },
);

async function handleTest() {
  await testSaved(props.connection.id);
  loadLogs();
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString(localeCode.value);
}
</script>

<template>
  <div class="detail">
    <!-- Compact fixed header -->
    <div class="detail-bar">
      <div class="detail-bar-left">
        <span class="detail-type">{{ connection.type }}</span>
        <h2 class="detail-name">{{ connection.connectionName }}</h2>
        <span class="detail-meta">{{
          t("detail.meta", {
            created: formatDate(connection.createdAt),
            updated: formatDate(connection.updatedAt),
          })
        }}</span>
      </div>
      <button
        class="btn btn-primary btn-sm"
        :disabled="testing"
        @click="handleTest"
      >
        {{ testing ? t("detail.testing") : t("detail.test") }}
      </button>
    </div>

    <!-- Tab navigation -->
    <el-tabs v-model="activeTab" class="detail-tabs">
      <!-- Tab: Overview -->
      <el-tab-pane :label="t('detail.tab.overview')" name="overview">
        <div class="tab-content">
          <PretextRenderer
            v-if="connection.description"
            :source="connection.description"
          />
          <div class="detail-grid">
            <section class="detail-panel">
              <div class="panel-head">
                <h4>{{ t("detail.maskedConfig") }}</h4>
                <span class="pill">{{ t("detail.securePreview") }}</span>
              </div>
              <JsonViewer :data="connection.maskedConfig" />
            </section>
            <section class="detail-panel">
              <div class="panel-head">
                <h4>{{ t("detail.validation") }}</h4>
                <span class="pill">{{ t("detail.immediateFeedback") }}</span>
              </div>
              <LoadingSpinner
                v-if="testing"
                :text="t('detail.runningChecks')"
              />
              <div
                v-else-if="testResult"
                class="test-result"
                :class="testResult.success ? 'result-ok' : 'result-fail'"
              >
                <StatusBadge
                  :status="testResult.success ? 'SUCCESS' : 'FAILED'"
                />
                <div>
                  <strong>{{ testResult.message }}</strong>
                  <p>{{ formatDate(testResult.testedAt) }}</p>
                </div>
              </div>
              <div v-else class="result-idle">
                {{ t("detail.idleValidation") }}
              </div>
              <div v-if="testError" class="test-result result-fail">
                {{ testError }}
              </div>
            </section>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab: Ingestion Config -->
      <el-tab-pane
        v-if="connection.type !== 'EXCEL'"
        :label="t('detail.tab.ingestion')"
        name="ingestion"
      >
        <div class="tab-content">
          <IngestionTrigger
            :connection-id="connection.id"
            :type="connection.type"
          />
        </div>
      </el-tab-pane>

      <!-- Tab: Results -->
      <el-tab-pane :label="t('detail.tab.result')" name="result">
        <div class="tab-content">
          <IngestionResult v-if="currentJob" :job="currentJob" />
          <div v-else class="result-idle">{{ t("detail.idleValidation") }}</div>
        </div>
      </el-tab-pane>

      <!-- Tab: Validation History -->
      <el-tab-pane :label="t('detail.tab.history')" name="history">
        <div class="tab-content">
          <div v-if="testLogs.length" class="log-list">
            <article v-for="log in testLogs" :key="log.id" class="log-item">
              <StatusBadge :status="log.success ? 'SUCCESS' : 'FAILED'" />
              <div class="log-copy">
                <strong>{{ log.message }}</strong>
                <span
                  >{{ log.dataSourceType }} /
                  {{ formatDate(log.testedAt) }}</span
                >
              </div>
            </article>
          </div>
          <div v-else class="result-idle">{{ t("detail.idleValidation") }}</div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  gap: 0;
}

/* ── Compact header bar ── */
.detail-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 4px;
}

.detail-bar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
}

.detail-name {
  font-family: var(--font-display);
  font-size: 20px;
  line-height: 1.1;
  letter-spacing: -0.03em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail-type {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
  color: var(--color-primary-hover);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  flex-shrink: 0;
}

.detail-meta {
  color: var(--color-text-muted);
  font-size: 11px;
  flex-shrink: 0;
}

/* ── Tab navigation ── */
.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.detail-tabs :deep(.el-tabs__nav-wrap::after) {
  background: rgba(255, 255, 255, 0.06);
  height: 1px;
}

.detail-tabs :deep(.el-tabs__item) {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  padding: 0 16px;
  height: 38px;
  line-height: 38px;
}

.detail-tabs :deep(.el-tabs__item.is-active) {
  color: var(--color-primary);
}

.detail-tabs :deep(.el-tabs__active-bar) {
  background: var(--color-primary);
}

/* ── Tab content ── */
.tab-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(240px, 0.9fr);
  gap: 14px;
}

.detail-panel {
  padding: 16px;
  border-radius: var(--radius-lg);
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.panel-head h4 {
  font-size: 14px;
  letter-spacing: -0.02em;
}

.test-result,
.result-idle {
  display: flex;
  align-items: start;
  gap: 10px;
  padding: 12px 14px;
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
  gap: 8px;
}

.log-item {
  display: flex;
  align-items: start;
  gap: 10px;
  padding: 12px 14px;
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
  .detail-bar {
    flex-direction: column;
    align-items: start;
  }

  .detail-name {
    font-size: 18px;
  }

  .detail-panel {
    padding: 12px;
  }
}
</style>
