<script setup lang="ts">
import { ref } from "vue";
import MysqlAiAssistant from "@/components/ingestion/MysqlAiAssistant.vue";
import type { MysqlAssistantSuggestedRequest } from "@/types/ai";
import type { MysqlBuilderSummaryItem } from "@/types/mysqlBuilder";

const props = defineProps<{
  connectionId: number;
  isZh: boolean;
  queryPreview: string;
  summaryItems: MysqlBuilderSummaryItem[];
  actionCopy: string;
  canExecute: boolean;
  executing: boolean;
  errorMessage: string | null;
}>();

const emit = defineEmits<{
  (e: "apply", request: MysqlAssistantSuggestedRequest): void;
  (e: "run"): void;
}>();

const expandedPanels = ref<string[]>([]);

function resolveTagType(tone?: MysqlBuilderSummaryItem["tone"]) {
  if (tone === "accent") {
    return "warning";
  }
  if (tone === "success") {
    return "success";
  }
  return "info";
}
</script>

<template>
  <aside class="sidebar-shell">
    <el-card shadow="never" class="sidebar-card">
      <template #header>
        <div class="sidebar-head">
          <h5>{{ isZh ? "摘要与执行" : "Summary + Run" }}</h5>
          <el-tag type="warning">{{
            isZh ? "固定侧栏" : "Pinned Rail"
          }}</el-tag>
        </div>
      </template>

      <el-descriptions
        :column="1"
        border
        size="small"
        class="summary-descriptions"
      >
        <el-descriptions-item
          v-for="item in props.summaryItems"
          :key="item.label"
          :label="item.label"
        >
          <el-tag
            v-if="item.tone"
            :type="resolveTagType(item.tone)"
            effect="plain"
          >
            {{ item.value }}
          </el-tag>
          <span v-else>{{ item.value }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-button
        type="primary"
        size="large"
        class="run-button"
        :disabled="executing || !canExecute"
        @click="emit('run')"
      >
        {{
          executing
            ? isZh
              ? "正在发起执行..."
              : "Launching Run..."
            : isZh
              ? "触发导入"
              : "Trigger Ingestion"
        }}
      </el-button>

      <p class="action-copy">{{ actionCopy }}</p>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
      />
    </el-card>

    <MysqlAiAssistant
      :connection-id="connectionId"
      @apply="emit('apply', $event)"
    />

    <el-card shadow="never" class="sidebar-card sidebar-card-collapsible">
      <el-collapse v-model="expandedPanels">
        <el-collapse-item name="query">
          <template #title>
            <div class="collapse-head">
              <span>{{ isZh ? "查询预览" : "Query Preview" }}</span>
              <el-tag type="info" effect="plain">{{
                isZh ? "按需展开" : "On Demand"
              }}</el-tag>
            </div>
          </template>

          <el-scrollbar max-height="240px">
            <pre class="query-preview">{{ queryPreview }}</pre>
          </el-scrollbar>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </aside>
</template>

<style scoped>
.sidebar-shell {
  position: sticky;
  top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sidebar-card h5 {
  margin: 0;
  font-size: 15px;
  letter-spacing: -0.02em;
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.query-preview {
  margin: 0;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: rgba(7, 17, 26, 0.76);
  border: 1px solid rgba(160, 186, 209, 0.08);
  color: var(--color-text-secondary);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.action-copy {
  margin: 10px 0 0;
  color: var(--color-text-muted);
  font-size: 11px;
  line-height: 1.5;
}

.run-button {
  width: 100%;
  margin-top: 10px;
}

.collapse-head {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-right: 4px;
}

@media (max-width: 1200px) {
  .sidebar-shell {
    position: static;
  }
}

@media (max-width: 720px) {
  .sidebar-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
