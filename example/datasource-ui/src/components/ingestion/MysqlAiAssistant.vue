<script setup lang="ts">
import { MagicStick, Promotion } from "@element-plus/icons-vue";
import { ref, watch } from "vue";
import {
  fetchMysqlAssistantHistory,
  streamMysqlAssistant,
} from "@/api/dataSource";
import { useLocale } from "@/composables/useLocale";
import type {
  MysqlAssistantChatResponse,
  MysqlAssistantHistoryEntry,
  MysqlAssistantSuggestedRequest,
} from "@/types/ai";
import { ApiError } from "@/types/common";

const props = defineProps<{ connectionId: number }>();
const emit = defineEmits<{
  (e: "apply", request: MysqlAssistantSuggestedRequest): void;
}>();

const { isZh, localeCode } = useLocale();
const DEFAULT_RESULT_FALLBACK_MESSAGE =
  "I reviewed the saved MySQL connection. You can continue refining the request.";

const message = ref("");
const sessionId = ref<string | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);
const statusMessage = ref<string | null>(null);
const response = ref<MysqlAssistantChatResponse | null>(null);
const historyVisible = ref(false);
const historyLoading = ref(false);
const historyError = ref<string | null>(null);
const historyEntries = ref<MysqlAssistantHistoryEntry[]>([]);

function buildAutoDetectPrompt() {
  return isZh.value
    ? "请自动检查当前 MySQL 连接中的所有表、字段和最多两条样例数据，优先识别最适合做导入的数据表，并直接生成可以应用到规则编辑器的导入配置。如果无法唯一确定，请给出最合理的一套配置并说明依据；如果没有样例数据，就基于字段完成配置。"
    : "Inspect the current MySQL connection, review tables, fields, and up to two sample rows, then produce a builder configuration that can be applied directly.";
}

function createStreamingResponse(
  currentSessionId?: string | null,
): MysqlAssistantChatResponse {
  return {
    sessionId: currentSessionId ?? "",
    assistantMessage: "",
    sourceSummary: null,
    suggestionAvailable: false,
    suggestedRequest: null,
    warnings: [],
    generatedAt: new Date().toISOString(),
  };
}

function resolveFinalAssistantMessage(
  streamedMessage: string | null | undefined,
  finalMessage: string | null | undefined,
) {
  const streamed = streamedMessage?.trim() ?? "";
  const resolved = finalMessage?.trim() ?? "";
  if (!resolved) {
    return streamed;
  }
  if (streamed && resolved === DEFAULT_RESULT_FALLBACK_MESSAGE) {
    return streamed;
  }
  return resolved;
}

function formatHistoryTime(value: string) {
  return new Date(value).toLocaleString(localeCode.value);
}

function shortenSessionId(value: string) {
  return value.length <= 12 ? value : `${value.slice(0, 12)}...`;
}

async function loadHistory() {
  historyLoading.value = true;
  historyError.value = null;
  try {
    historyEntries.value = await fetchMysqlAssistantHistory(props.connectionId);
  } catch (err) {
    historyError.value =
      err instanceof ApiError
        ? err.message
        : isZh.value
          ? "加载 AI 历史失败，请稍后重试。"
          : "Failed to load AI history.";
  } finally {
    historyLoading.value = false;
  }
}

async function openHistory() {
  historyVisible.value = true;
  await loadHistory();
}

async function requestAssistant(options?: {
  presetMessage?: string;
  autoApply?: boolean;
}) {
  const effectiveMessage = (
    message.value.trim() ||
    options?.presetMessage ||
    ""
  ).trim();
  if (!effectiveMessage) {
    error.value = isZh.value
      ? "请先说明你想让 AI 帮你生成什么导入配置。"
      : "Describe the ingestion config you want first.";
    return;
  }

  loading.value = true;
  error.value = null;
  statusMessage.value = isZh.value
    ? "正在建立 SSE 流式会话..."
    : "Opening SSE stream...";
  response.value = createStreamingResponse(sessionId.value);

  try {
    await streamMysqlAssistant(
      props.connectionId,
      {
        sessionId: sessionId.value,
        message: effectiveMessage,
      },
      {
        onSession(payload) {
          sessionId.value = payload.sessionId;
          response.value = {
            ...(response.value ?? createStreamingResponse(payload.sessionId)),
            sessionId: payload.sessionId,
          };
          statusMessage.value = isZh.value
            ? "AI 正在流式生成回复..."
            : "The AI reply is streaming...";
        },
        onDelta(payload) {
          const current =
            response.value ?? createStreamingResponse(sessionId.value);
          response.value = {
            ...current,
            assistantMessage: `${current.assistantMessage}${payload.content}`,
          };
        },
        onResult(payload) {
          const streamedMessage = response.value?.assistantMessage;
          sessionId.value = payload.sessionId;
          response.value = {
            ...payload,
            assistantMessage: resolveFinalAssistantMessage(
              streamedMessage,
              payload.assistantMessage,
            ),
          };

          if (options?.autoApply) {
            if (payload.suggestionAvailable && payload.suggestedRequest) {
              emit("apply", payload.suggestedRequest);
              statusMessage.value = isZh.value
                ? "AI 已流式完成，并自动填充到下方规则编辑器。"
                : "The AI stream is complete and the suggestion has been applied.";
            } else {
              statusMessage.value = isZh.value
                ? "AI 已流式完成，但当前没有可直接应用的配置。"
                : "The AI stream is complete, but there is no suggestion to apply directly yet.";
            }
          } else {
            statusMessage.value = isZh.value
              ? "AI 流式回复完成。"
              : "The AI streaming reply is complete.";
          }

          if (historyVisible.value) {
            void loadHistory();
          }
        },
        onDone() {
          if (!statusMessage.value) {
            statusMessage.value = isZh.value
              ? "AI 流式回复完成。"
              : "The AI streaming reply is complete.";
          }
        },
      },
    );
  } catch (err) {
    error.value =
      err instanceof ApiError
        ? err.message
        : isZh.value
          ? "AI 助手暂时不可用，请稍后重试。"
          : "The AI assistant is temporarily unavailable.";

    if (!response.value?.assistantMessage) {
      response.value = null;
    }
  } finally {
    loading.value = false;
  }
}

async function handleAsk() {
  await requestAssistant();
}

async function handleAutoDetect() {
  await requestAssistant({
    presetMessage: buildAutoDetectPrompt(),
    autoApply: true,
  });
}

function handleApply() {
  if (!response.value?.suggestedRequest) {
    return;
  }
  emit("apply", response.value.suggestedRequest);
  statusMessage.value = isZh.value
    ? "已将当前 AI 建议填充到规则编辑器。"
    : "The current AI suggestion has been applied to the builder.";
}

function handleApplyHistory(entry: MysqlAssistantHistoryEntry) {
  if (!entry.suggestedRequest) {
    return;
  }
  emit("apply", entry.suggestedRequest);
  statusMessage.value = isZh.value
    ? "已将历史 AI 建议填充到规则编辑器。"
    : "The historical AI suggestion has been applied to the builder.";
  historyVisible.value = false;
}

function warningType() {
  return response.value?.warnings.length ? "warning" : "info";
}

watch(
  () => props.connectionId,
  () => {
    historyVisible.value = false;
    historyError.value = null;
    historyEntries.value = [];
  },
);
</script>

<template>
  <el-card shadow="never" class="assistant-card">
    <template #header>
      <div class="assistant-head">
        <div>
          <div class="assistant-title-row">
            <h5>{{ isZh ? "AI 辅助填充" : "AI Autofill" }}</h5>
            <el-tag effect="dark">Spring AI SSE</el-tag>
          </div>
          <p class="assistant-copy">
            {{
              isZh
                ? "输入一句要求或直接自动识别，AI 会流式返回文本，并在可用时返回可应用的结构化配置。"
                : "Use a short prompt or auto-detect. The assistant streams text and returns a structured suggestion when available."
            }}
          </p>
        </div>
      </div>
    </template>

    <el-form label-position="top" class="assistant-form">
      <el-form-item :label="isZh ? '补充要求' : 'Prompt Details'">
        <el-input
          v-model="message"
          type="textarea"
          :rows="4"
          :placeholder="
            isZh
              ? '例如：优先使用订单表，提取订单号、用户、金额和更新时间。'
              : 'Example: prefer the orders table, extract id, user, amount, and updated time.'
          "
        />
      </el-form-item>

      <div class="assistant-actions">
        <el-button
          type="primary"
          :icon="MagicStick"
          :loading="loading"
          @click="handleAutoDetect"
        >
          {{ isZh ? "自动识别并填充" : "Auto Detect & Fill" }}
        </el-button>

        <el-button :icon="Promotion" :disabled="loading" @click="handleAsk">
          {{ isZh ? "按当前要求生成" : "Stream From Prompt" }}
        </el-button>

        <el-button text :disabled="loading" @click="openHistory">
          {{ isZh ? "查看历史" : "History" }}
        </el-button>

        <el-button
          v-if="response?.suggestionAvailable && response.suggestedRequest"
          text
          type="warning"
          @click="handleApply"
        >
          {{ isZh ? "应用当前建议" : "Apply Suggestion" }}
        </el-button>
      </div>
    </el-form>

    <el-alert
      v-if="statusMessage"
      :title="statusMessage"
      type="info"
      :closable="false"
      show-icon
      class="assistant-status"
    />

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
      class="assistant-status"
    />

    <div v-if="response" class="assistant-result">
      <div class="assistant-result-head">
        <div class="assistant-result-title">
          <h6>{{ isZh ? "AI 回复" : "Assistant Reply" }}</h6>
          <el-tag :type="response.suggestionAvailable ? 'success' : 'info'">
            {{
              response.suggestionAvailable
                ? isZh
                  ? "可应用"
                  : "Ready"
                : isZh
                  ? "仅回复"
                  : "Reply only"
            }}
          </el-tag>
        </div>

        <el-text class="assistant-session">
          {{ isZh ? "会话可持续追问" : "Session can be reused" }}
        </el-text>
      </div>

      <div class="assistant-message">
        {{
          response.assistantMessage ||
          (isZh ? "AI 正在生成中..." : "The AI reply is streaming...")
        }}
      </div>

      <el-alert
        v-if="response.sourceSummary"
        :title="isZh ? '数据依据' : 'Source Summary'"
        :description="response.sourceSummary"
        :type="warningType()"
        :closable="false"
        show-icon
      />

      <el-alert
        v-if="response.warnings.length"
        :title="isZh ? '注意事项' : 'Warnings'"
        type="warning"
        :closable="false"
        show-icon
      >
        <ul class="warning-list">
          <li v-for="warning in response.warnings" :key="warning">
            {{ warning }}
          </li>
        </ul>
      </el-alert>
    </div>

    <el-drawer
      v-model="historyVisible"
      :title="isZh ? 'AI 执行历史' : 'AI History'"
      size="620px"
      class="assistant-history-drawer"
    >
      <div class="assistant-history-head">
        <el-tag type="info">
          {{
            isZh
              ? `共 ${historyEntries.length} 条记录`
              : `${historyEntries.length} entries`
          }}
        </el-tag>
        <el-button text @click="loadHistory">
          {{ isZh ? "刷新" : "Refresh" }}
        </el-button>
      </div>

      <el-alert
        v-if="historyError"
        :title="historyError"
        type="error"
        :closable="false"
        show-icon
        class="assistant-history-status"
      />

      <div v-if="historyLoading" class="assistant-history-loading">
        <el-skeleton :rows="5" animated />
      </div>

      <el-empty
        v-else-if="!historyEntries.length"
        :description="isZh ? '还没有 AI 执行历史。' : 'No AI history yet.'"
      />

      <div v-else class="assistant-history-list">
        <el-card
          v-for="entry in historyEntries"
          :key="entry.id"
          shadow="never"
          class="assistant-history-item"
        >
          <div class="assistant-history-item-head">
            <div class="assistant-history-meta">
              <strong>{{ formatHistoryTime(entry.generatedAt) }}</strong>
              <el-tag effect="plain" size="small">
                {{ shortenSessionId(entry.sessionId) }}
              </el-tag>
            </div>
            <el-tag :type="entry.suggestionAvailable ? 'success' : 'info'">
              {{
                entry.suggestionAvailable
                  ? isZh
                    ? "可应用"
                    : "Ready"
                  : isZh
                    ? "仅回复"
                    : "Reply only"
              }}
            </el-tag>
          </div>

          <div class="assistant-history-block">
            <span class="assistant-history-label">
              {{ isZh ? "要求" : "Prompt" }}
            </span>
            <p class="assistant-history-text">{{ entry.userMessage }}</p>
          </div>

          <div class="assistant-history-block">
            <span class="assistant-history-label">
              {{ isZh ? "回复" : "Assistant" }}
            </span>
            <p class="assistant-history-text">{{ entry.assistantMessage }}</p>
          </div>

          <div v-if="entry.sourceSummary" class="assistant-history-block">
            <span class="assistant-history-label">
              {{ isZh ? "数据依据" : "Source Summary" }}
            </span>
            <p class="assistant-history-text">{{ entry.sourceSummary }}</p>
          </div>

          <div v-if="entry.warnings.length" class="assistant-history-block">
            <span class="assistant-history-label">
              {{ isZh ? "注意事项" : "Warnings" }}
            </span>
            <ul class="warning-list">
              <li v-for="warning in entry.warnings" :key="`${entry.id}-${warning}`">
                {{ warning }}
              </li>
            </ul>
          </div>

          <div
            v-if="entry.suggestionAvailable && entry.suggestedRequest"
            class="assistant-history-actions"
          >
            <el-button text type="warning" @click="handleApplyHistory(entry)">
              {{ isZh ? "应用这条建议" : "Apply Suggestion" }}
            </el-button>
          </div>
        </el-card>
      </div>
    </el-drawer>
  </el-card>
</template>

<style scoped>
.assistant-card {
  border-radius: var(--radius-md);
}

.assistant-head h5,
.assistant-result-title h6 {
  margin: 0;
  font-size: 14px;
  letter-spacing: -0.02em;
}

.assistant-title-row,
.assistant-result-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.assistant-copy {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  font-size: 11px;
  line-height: 1.5;
}

.assistant-form,
.assistant-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assistant-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.assistant-status {
  margin-top: 8px;
}

.assistant-result {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(160, 186, 209, 0.1);
}

.assistant-result-head,
.assistant-history-head,
.assistant-history-item-head,
.assistant-history-meta,
.assistant-history-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.assistant-message {
  white-space: pre-wrap;
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.assistant-session {
  color: var(--color-text-muted);
}

.assistant-history-status {
  margin-top: 12px;
}

.assistant-history-loading,
.assistant-history-list {
  margin-top: 14px;
}

.assistant-history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.assistant-history-item {
  border-radius: var(--radius-md);
}

.assistant-history-meta {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.assistant-history-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
}

.assistant-history-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.assistant-history-text {
  margin: 0;
  white-space: pre-wrap;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.assistant-history-actions {
  justify-content: flex-end;
  margin-top: 12px;
}

.warning-list {
  margin: 0;
  padding-left: 18px;
}

@media (max-width: 720px) {
  .assistant-result-head,
  .assistant-history-head,
  .assistant-history-item-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
