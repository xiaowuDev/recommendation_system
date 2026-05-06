<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { fetchConnection } from "@/api/dataSource";
import ExcelUpload from "@/components/excel/ExcelUpload.vue";
import IngestionJobList from "@/components/ingestion/IngestionJobList.vue";
import IngestionResult from "@/components/ingestion/IngestionResult.vue";
import IngestionTrigger from "@/components/ingestion/IngestionTrigger.vue";
import { useConnections } from "@/composables/useConnections";
import { useIngestionJob } from "@/composables/useIngestionJob";
import { useLocale } from "@/composables/useLocale";
import type { DataSourceConnection } from "@/types/connection";

const { connections, loadConnections } = useConnections();
const { currentJob, recentJobs, loadRecentJobs } = useIngestionJob();
const { t, isZh } = useLocale();

const selectedId = ref<number | undefined>();
const selectedConn = ref<DataSourceConnection | null>(null);
const viewMode = ref<"connections" | "excel">("connections");
const historyExpanded = ref(false);

const nonExcelConnections = computed(() =>
  connections.value.filter((conn) => conn.type !== "EXCEL"),
);

onMounted(() => loadConnections());

watch(selectedId, async (id) => {
  if (id == null) {
    selectedConn.value = null;
    return;
  }

  try {
    selectedConn.value = await fetchConnection(id);
    loadRecentJobs(id);
    historyExpanded.value = false;
  } catch {
    selectedConn.value = null;
  }
});
</script>

<template>
  <div class="page-shell ingestion-page">
    <el-card shadow="never" class="hero-card">
      <div class="hero-layout">
        <div class="hero-copy">
          <span class="section-eyebrow">{{ t("ingestion.eyebrow") }}</span>
          <h1 class="page-title hero-title">{{ t("ingestion.title") }}</h1>
          <p class="hero-description">{{ t("ingestion.description") }}</p>
        </div>

        <el-radio-group v-model="viewMode" size="large" class="mode-switch">
          <el-radio-button label="connections">
            {{ t("ingestion.tab.connections") }}
          </el-radio-button>
          <el-radio-button label="excel">
            {{ t("ingestion.tab.excel") }}
          </el-radio-button>
        </el-radio-group>
      </div>
    </el-card>

    <template v-if="viewMode === 'excel'">
      <ExcelUpload />
    </template>

    <template v-else>
      <el-card shadow="never" class="panel-card">
        <template #header>
          <div class="panel-head">
            <div>
              <h2>{{ t("ingestion.connectionRunsTitle") }}</h2>
              <p class="panel-copy">{{ t("ingestion.connectionRunsDesc") }}</p>
            </div>
            <el-tag type="info" effect="dark">
              {{
                t("ingestion.available", { count: nonExcelConnections.length })
              }}
            </el-tag>
          </div>
        </template>

        <el-form label-position="top" class="picker-form">
          <el-form-item :label="t('ingestion.selectConnection')">
            <el-select
              v-model="selectedId"
              clearable
              filterable
              :placeholder="t('ingestion.chooseConnection')"
              class="picker-select"
            >
              <el-option
                v-for="conn in nonExcelConnections"
                :key="conn.id"
                :label="`[${conn.type}] ${conn.connectionName}`"
                :value="conn.id"
              />
            </el-select>
          </el-form-item>
        </el-form>

        <div v-if="selectedConn" class="workspace-stack">
          <el-card shadow="never" class="workspace-card">
            <template #header>
              <div class="workspace-head">
                <div>
                  <h3>{{ selectedConn.connectionName }}</h3>
                  <p class="panel-copy">
                    {{
                      t("ingestion.tuneExecution", {
                        name: selectedConn.connectionName,
                      })
                    }}
                  </p>
                </div>

                <div class="workspace-meta">
                  <el-tag effect="plain">{{ selectedConn.type }}</el-tag>
                  <el-button @click="historyExpanded = true">
                    {{ isZh ? "查看历史" : "Recent Jobs" }}
                  </el-button>
                </div>
              </div>
            </template>

            <IngestionTrigger
              :connection-id="selectedConn.id"
              :type="selectedConn.type"
            />
          </el-card>

          <IngestionResult v-if="currentJob" :job="currentJob" />
        </div>

        <el-empty v-else :description="t('ingestion.empty')" />
      </el-card>

      <el-drawer
        v-model="historyExpanded"
        :title="t('ingestion.recentJobs')"
        size="720px"
        class="ingestion-history-drawer"
      >
        <div class="drawer-head">
          <el-tag type="info">
            {{ t("ingestion.runs", { count: recentJobs.length }) }}
          </el-tag>
          <el-text>{{
            isZh
              ? "不占主阅读路径，按需展开查看。"
              : "Kept out of the main reading path and opened on demand."
          }}</el-text>
        </div>

        <IngestionJobList :jobs="recentJobs" />
      </el-drawer>
    </template>
  </div>
</template>

<style scoped>
.ingestion-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.hero-card {
  overflow: hidden;
  border: 1px solid rgba(225, 191, 120, 0.16);
  border-radius: var(--radius-xl);
  background:
    radial-gradient(
      ellipse 60% 50% at 85% 15%,
      rgba(225, 191, 120, 0.14),
      transparent
    ),
    radial-gradient(
      ellipse 50% 60% at 10% 85%,
      rgba(123, 207, 255, 0.12),
      transparent
    ),
    linear-gradient(160deg, rgba(13, 28, 42, 0.96), rgba(8, 16, 24, 0.92));
}

.hero-layout,
.panel-head,
.workspace-head,
.workspace-meta,
.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel-card h2,
.workspace-card h3 {
  margin: 0 0 6px;
  font-size: 22px;
  font-family: var(--font-display);
  letter-spacing: -0.03em;
}

.panel-copy,
.drawer-head :deep(.el-text) {
  margin: 0;
  color: var(--color-text-muted);
  line-height: 1.7;
}

.picker-form {
  max-width: 480px;
}

.picker-select {
  width: 100%;
}

.workspace-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drawer-head {
  margin-bottom: 16px;
}

@media (max-width: 720px) {
  .hero-layout,
  .panel-head,
  .workspace-head,
  .workspace-meta,
  .drawer-head {
    flex-direction: column;
  }

  .mode-switch {
    width: 100%;
  }
}
</style>
