<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { fetchMysqlTransformRules } from "@/api/dataSource";
import MysqlBuilderSidebar from "@/components/ingestion/mysql/MysqlBuilderSidebar.vue";
import MysqlBuilderStepList from "@/components/ingestion/mysql/MysqlBuilderStepList.vue";
import MysqlFieldMappingsSection from "@/components/ingestion/mysql/MysqlFieldMappingsSection.vue";
import MysqlFilterTransformSection from "@/components/ingestion/mysql/MysqlFilterTransformSection.vue";
import MysqlSourceConfigSection from "@/components/ingestion/mysql/MysqlSourceConfigSection.vue";
import MysqlTargetConfigSection from "@/components/ingestion/mysql/MysqlTargetConfigSection.vue";
import { useIngestionJob } from "@/composables/useIngestionJob";
import { useLocale } from "@/composables/useLocale";
import type { MysqlAssistantSuggestedRequest } from "@/types/ai";
import type { MysqlTransformRuleCapability } from "@/types/ingestion";
import type {
  MysqlBuilderSummaryItem,
  MysqlBuilderTab,
  MysqlBuilderTabId,
  MysqlFieldMappingRule,
  MysqlFilterRule,
  MysqlSourceConfig,
  MysqlTargetConfig,
  MysqlTransformRule,
} from "@/types/mysqlBuilder";

const props = defineProps<{ connectionId: number; type: string }>();
const { execute, executing, jobError } = useIngestionJob();
const { t, isZh } = useLocale();

const kafkaMaxRecords = ref(100);
const kafkaTimeout = ref(10000);

const mysqlSourceConfig = ref<MysqlSourceConfig>({
  useCustomQuery: false,
  customQuery: "SELECT * FROM ",
  sourceTable: "",
  sortField: "",
  sortDirection: "DESC",
  maxRows: 1000,
});
const mysqlFieldMappings = ref<MysqlFieldMappingRule[]>([
  { sourceField: "", targetField: "", enabled: true },
]);
const mysqlFilters = ref<MysqlFilterRule[]>([]);
const mysqlTransformRules = ref<MysqlTransformRule[]>([]);
const mysqlTargetConfig = ref<MysqlTargetConfig>({
  targetName: "",
  writeMode: "APPEND",
  primaryKey: "",
  incrementalField: "",
});
const mysqlTransformCapabilities = ref<MysqlTransformRuleCapability[]>([]);
const activeMysqlTab = ref<MysqlBuilderTabId>("source");

const redisKeyPattern = ref("*");
const redisScanCount = ref(100);
const redisMaxKeys = ref(100);

const localError = ref<string | null>(null);

function deriveAlias(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return "";
  const lastDot = trimmed.lastIndexOf(".");
  return lastDot >= 0 ? trimmed.slice(lastDot + 1) : trimmed;
}

function resolveDefaultTransformType(): string {
  return mysqlTransformCapabilities.value[0]?.code ?? "TRIM";
}

function resolveMysqlStepStatus(
  stepId: MysqlBuilderTabId,
): MysqlBuilderTab["status"] {
  if (activeMysqlTab.value === stepId) {
    return "active";
  }

  switch (stepId) {
    case "source":
      return mysqlSourceConfig.value.useCustomQuery
        ? mysqlSourceConfig.value.customQuery.trim()
          ? "done"
          : "todo"
        : mysqlSourceConfig.value.sourceTable.trim()
          ? "done"
          : "todo";
    case "mapping":
      return enabledMysqlFieldMappings.value.length > 0 ? "done" : "todo";
    case "transform":
      return enabledMysqlFilters.value.length > 0 ||
        enabledMysqlTransformRules.value.length > 0
        ? "done"
        : "todo";
    case "target":
      return mysqlTargetConfig.value.targetName.trim() ? "done" : "todo";
    default:
      return "todo";
  }
}

async function loadMysqlTransformCapabilities() {
  if (props.type !== "MYSQL") {
    return;
  }

  try {
    mysqlTransformCapabilities.value = await fetchMysqlTransformRules();
    mysqlTransformRules.value = mysqlTransformRules.value.map((rule) => ({
      ...rule,
      transformType: rule.transformType || resolveDefaultTransformType(),
    }));
  } catch (error) {
    console.error("Failed to load MySQL transform capabilities", error);
  }
}

const enabledMysqlFieldMappings = computed(() =>
  mysqlFieldMappings.value
    .filter((rule) => rule.enabled && rule.sourceField.trim())
    .map((rule) => ({
      sourceField: rule.sourceField.trim(),
      targetField: (
        rule.targetField.trim() || deriveAlias(rule.sourceField)
      ).trim(),
      enabled: true,
    })),
);

const enabledMysqlFilters = computed(() =>
  mysqlFilters.value
    .filter((rule) => rule.enabled && rule.field.trim())
    .map((rule) => ({
      logic: rule.logic,
      field: rule.field.trim(),
      operator: rule.operator,
      value: rule.value.trim() || null,
      enabled: true,
    })),
);

const enabledMysqlTransformRules = computed(() =>
  mysqlTransformRules.value
    .filter((rule) => rule.enabled && rule.field.trim())
    .map((rule) => ({
      field: rule.field.trim(),
      targetField: rule.targetField.trim() || rule.field.trim(),
      transformType: rule.transformType,
      argument: rule.argument.trim() || null,
      enabled: true,
    })),
);

const generatedMysqlQuery = computed(() => {
  if (mysqlSourceConfig.value.useCustomQuery) {
    return mysqlSourceConfig.value.customQuery;
  }

  const table = mysqlSourceConfig.value.sourceTable.trim();
  const mappings = enabledMysqlFieldMappings.value;
  if (!table) {
    return isZh.value ? "请先填写源表名称。" : "Provide a source table first.";
  }
  if (!mappings.length) {
    return isZh.value
      ? "请至少添加一条启用的字段映射。"
      : "Add at least one enabled field mapping.";
  }

  const selectClause = mappings
    .map((rule) => `${rule.sourceField} AS ${rule.targetField}`)
    .join(", ");

  let sql = `SELECT ${selectClause} FROM ${table}`;

  if (enabledMysqlFilters.value.length) {
    const whereClause = enabledMysqlFilters.value
      .map((rule, index) => {
        const prefix = index === 0 ? "" : ` ${rule.logic} `;
        if (rule.operator === "IS_NULL") {
          return `${prefix}${rule.field} IS NULL`;
        }
        if (rule.operator === "NOT_NULL") {
          return `${prefix}${rule.field} IS NOT NULL`;
        }
        if (rule.operator === "IN") {
          return `${prefix}${rule.field} IN (${rule.value ?? ""})`;
        }

        const sqlOperator = {
          EQ: "=",
          NE: "<>",
          GT: ">",
          GTE: ">=",
          LT: "<",
          LTE: "<=",
          LIKE: "LIKE",
        }[rule.operator];
        return `${prefix}${rule.field} ${sqlOperator} '${rule.value ?? ""}'`;
      })
      .join("");
    sql += ` WHERE ${whereClause}`;
  }

  const sortField =
    mysqlSourceConfig.value.sortField.trim() ||
    mysqlTargetConfig.value.incrementalField.trim();
  if (sortField) {
    sql += ` ORDER BY ${sortField} ${mysqlSourceConfig.value.sortDirection}`;
  }

  return sql;
});

const mysqlParams = computed<Record<string, unknown>>(() => ({
  query: mysqlSourceConfig.value.useCustomQuery
    ? mysqlSourceConfig.value.customQuery.trim()
    : null,
  sourceTable: mysqlSourceConfig.value.useCustomQuery
    ? null
    : mysqlSourceConfig.value.sourceTable.trim() || null,
  fieldMappings: enabledMysqlFieldMappings.value,
  filters: mysqlSourceConfig.value.useCustomQuery
    ? []
    : enabledMysqlFilters.value,
  transformRules: enabledMysqlTransformRules.value,
  target: {
    targetName: mysqlTargetConfig.value.targetName.trim() || null,
    writeMode: mysqlTargetConfig.value.writeMode,
    primaryKey: mysqlTargetConfig.value.primaryKey.trim() || null,
    incrementalField: mysqlTargetConfig.value.incrementalField.trim() || null,
  },
  sortField: mysqlSourceConfig.value.useCustomQuery
    ? null
    : mysqlSourceConfig.value.sortField.trim() || null,
  sortDirection: mysqlSourceConfig.value.useCustomQuery
    ? null
    : mysqlSourceConfig.value.sortDirection,
  maxRows: mysqlSourceConfig.value.maxRows,
}));

const params = computed<Record<string, unknown>>(() => {
  switch (props.type) {
    case "KAFKA":
      return {
        maxRecords: kafkaMaxRecords.value,
        timeoutMs: kafkaTimeout.value,
      };
    case "MYSQL":
      return mysqlParams.value;
    case "REDIS":
      return {
        keyPattern: redisKeyPattern.value,
        scanCount: redisScanCount.value,
        maxKeys: redisMaxKeys.value,
      };
    default:
      return {};
  }
});

const canExecute = computed(() => {
  if (props.type === "MYSQL") {
    if (mysqlSourceConfig.value.useCustomQuery) {
      return !!mysqlSourceConfig.value.customQuery.trim();
    }
    return (
      !!mysqlSourceConfig.value.sourceTable.trim() &&
      enabledMysqlFieldMappings.value.length > 0
    );
  }
  return true;
});

const actionCopy = computed(() => {
  if (props.type === "MYSQL") {
    return isZh.value
      ? "所有配置都收敛在这一页里，确认摘要后就可以直接触发本次导入。"
      : "Everything for this run now lives on one surface, so you can verify the summary and launch without losing context.";
  }
  return t("trigger.actionCopy");
});

const mysqlTabItems = computed<MysqlBuilderTab[]>(() => [
  {
    id: "source",
    label: isZh.value ? "源" : "Source",
    meta: mysqlSourceConfig.value.useCustomQuery
      ? isZh.value
        ? "自定义 SQL"
        : "Custom SQL"
      : mysqlSourceConfig.value.sourceTable.trim() ||
        (isZh.value ? "待选择" : "Pending"),
    status: resolveMysqlStepStatus("source"),
  },
  {
    id: "mapping",
    label: isZh.value ? "映射" : "Mapping",
    meta: isZh.value
      ? `${enabledMysqlFieldMappings.value.length} 条启用`
      : `${enabledMysqlFieldMappings.value.length} active`,
    status: resolveMysqlStepStatus("mapping"),
  },
  {
    id: "transform",
    label: isZh.value ? "过滤与转换" : "Filter + Transform",
    meta: `${enabledMysqlFilters.value.length} / ${enabledMysqlTransformRules.value.length}`,
    status: resolveMysqlStepStatus("transform"),
  },
  {
    id: "target",
    label: isZh.value ? "目标" : "Target",
    meta:
      mysqlTargetConfig.value.targetName.trim() ||
      mysqlTargetConfig.value.writeMode,
    status: resolveMysqlStepStatus("target"),
  },
]);

const mysqlSummaryItems = computed<MysqlBuilderSummaryItem[]>(() => [
  {
    label: isZh.value ? "模式" : "Mode",
    value: mysqlSourceConfig.value.useCustomQuery
      ? isZh.value
        ? "自定义 SQL"
        : "Custom SQL"
      : isZh.value
        ? "规则构建器"
        : "Rule Builder",
    tone: "accent",
  },
  {
    label: isZh.value ? "源表" : "Source",
    value: mysqlSourceConfig.value.useCustomQuery
      ? isZh.value
        ? "由 SQL 决定"
        : "Driven by SQL"
      : mysqlSourceConfig.value.sourceTable.trim() ||
        (isZh.value ? "未设置" : "Unset"),
  },
  {
    label: isZh.value ? "字段映射" : "Mappings",
    value: isZh.value
      ? `${enabledMysqlFieldMappings.value.length} 条`
      : `${enabledMysqlFieldMappings.value.length} rules`,
  },
  {
    label: isZh.value ? "过滤条件" : "Filters",
    value: isZh.value
      ? `${enabledMysqlFilters.value.length} 条`
      : `${enabledMysqlFilters.value.length} rules`,
  },
  {
    label: isZh.value ? "转换规则" : "Transforms",
    value: isZh.value
      ? `${enabledMysqlTransformRules.value.length} 条`
      : `${enabledMysqlTransformRules.value.length} rules`,
  },
  {
    label: isZh.value ? "目标表" : "Target",
    value:
      mysqlTargetConfig.value.targetName.trim() ||
      (isZh.value ? "未设置" : "Unset"),
  },
  {
    label: isZh.value ? "写入模式" : "Write Mode",
    value: mysqlTargetConfig.value.writeMode,
  },
  {
    label: isZh.value ? "执行状态" : "Ready",
    value: canExecute.value
      ? isZh.value
        ? "可以执行"
        : "Ready to run"
      : isZh.value
        ? "仍需补全"
        : "Needs input",
    tone: canExecute.value ? "success" : "default",
  },
]);

const activeMysqlStep = computed(
  () =>
    mysqlTabItems.value.find((step) => step.id === activeMysqlTab.value) ??
    mysqlTabItems.value[0],
);

watch(
  params,
  () => {
    localError.value = null;
  },
  { deep: true },
);

onMounted(() => {
  void loadMysqlTransformCapabilities();
});

watch(
  () => props.type,
  (type) => {
    if (type === "MYSQL" && mysqlTransformCapabilities.value.length === 0) {
      void loadMysqlTransformCapabilities();
    }
  },
);

function handleExecute() {
  if (!canExecute.value) {
    localError.value = isZh.value
      ? "请先补全当前流程中的关键字段，再触发导入。"
      : "Complete the required fields in the current workflow before running.";
    return;
  }
  execute(props.connectionId, params.value);
}

function applyAiSuggestion(request: MysqlAssistantSuggestedRequest) {
  mysqlSourceConfig.value = {
    useCustomQuery: !!request.query?.trim(),
    customQuery: request.query?.trim() || "SELECT * FROM ",
    sourceTable: request.sourceTable?.trim() || "",
    sortField: request.sortField?.trim() || "",
    sortDirection: request.sortDirection === "ASC" ? "ASC" : "DESC",
    maxRows: request.maxRows && request.maxRows > 0 ? request.maxRows : 1000,
  };

  mysqlFieldMappings.value = request.fieldMappings?.length
    ? request.fieldMappings.map((rule) => ({
        sourceField: rule.sourceField?.trim() || "",
        targetField: rule.targetField?.trim() || "",
        enabled: rule.enabled ?? true,
      }))
    : [{ sourceField: "", targetField: "", enabled: true }];

  mysqlFilters.value = request.filters?.length
    ? request.filters.map((rule, index) => ({
        logic: index === 0 ? "AND" : rule.logic === "OR" ? "OR" : "AND",
        field: rule.field?.trim() || "",
        operator: rule.operator ?? "EQ",
        value: rule.value?.trim() || "",
        enabled: rule.enabled ?? true,
      }))
    : [];

  mysqlTransformRules.value = request.transformRules?.length
    ? request.transformRules.map((rule) => ({
        field: rule.field?.trim() || "",
        targetField: rule.targetField?.trim() || "",
        transformType: rule.transformType ?? resolveDefaultTransformType(),
        argument: rule.argument?.trim() || "",
        enabled: rule.enabled ?? true,
      }))
    : [];

  mysqlTargetConfig.value = {
    targetName: request.target?.targetName?.trim() || "",
    writeMode: request.target?.writeMode ?? "APPEND",
    primaryKey: request.target?.primaryKey?.trim() || "",
    incrementalField: request.target?.incrementalField?.trim() || "",
  };

  activeMysqlTab.value = mysqlSourceConfig.value.useCustomQuery
    ? "source"
    : "mapping";
  localError.value = null;
}
</script>

<template>
  <div class="trigger-form">
    <template v-if="type === 'KAFKA'">
      <el-card shadow="never">
        <el-form label-position="top">
          <el-row :gutter="16">
            <el-col :xs="24" :md="12">
              <el-form-item :label="t('trigger.maxRecords')">
                <el-input-number
                  v-model="kafkaMaxRecords"
                  :min="1"
                  controls-position="right"
                  class="full-width"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="t('trigger.timeout')">
                <el-input-number
                  v-model="kafkaTimeout"
                  :min="100"
                  controls-position="right"
                  class="full-width"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>
    </template>

    <template v-else-if="type === 'MYSQL'">
      <section class="builder-shell">
        <div class="builder-head">
          <div>
            <h4 class="builder-title">
              {{ isZh ? "MySQL 单页配置台" : "MySQL Single-Page Builder" }}
            </h4>
            <p class="builder-copy">
              {{
                isZh
                  ? "把源、映射、过滤、转换和目标配置压缩在一个工作台里，避免来回滚动和跳页。"
                  : "Keep source, mapping, filter, transform, and target settings in one compact workspace instead of one long scroll."
              }}
            </p>
          </div>
          <el-tag effect="dark" type="warning">
            {{ isZh ? "Element Plus 工作台" : "Element Plus Studio" }}
          </el-tag>
        </div>

        <el-row :gutter="12" class="builder-layout">
          <el-col :xs="24" :lg="6" :xl="5">
            <el-card shadow="never" class="builder-card builder-step-card">
              <template #header>
                <div class="card-head">
                  <div>
                    <div class="card-kicker">
                      {{ isZh ? "配置列表" : "Config List" }}
                    </div>
                    <h5>{{ isZh ? "逐项配置" : "Step List" }}</h5>
                  </div>
                </div>
              </template>

              <MysqlBuilderStepList
                :steps="mysqlTabItems"
                :active-step="activeMysqlTab"
                @select="activeMysqlTab = $event"
              />
            </el-card>
          </el-col>

          <el-col :xs="24" :lg="18" :xl="13">
            <el-card shadow="never" class="builder-card builder-stage">
              <template #header>
                <div class="stage-head">
                  <div class="stage-copy">
                    <div class="card-kicker">
                      {{ isZh ? "当前步骤" : "Current Step" }}
                    </div>
                    <h5>{{ activeMysqlStep.label }}</h5>
                  </div>
                  <el-tag type="info" effect="plain">{{
                    activeMysqlStep.meta
                  }}</el-tag>
                </div>
              </template>

              <MysqlSourceConfigSection
                v-if="activeMysqlTab === 'source'"
                v-model:source-config="mysqlSourceConfig"
                :is-zh="isZh"
              />

              <MysqlFieldMappingsSection
                v-else-if="activeMysqlTab === 'mapping'"
                v-model="mysqlFieldMappings"
                :is-zh="isZh"
              />

              <MysqlFilterTransformSection
                v-else-if="activeMysqlTab === 'transform'"
                v-model:filters="mysqlFilters"
                v-model:transform-rules="mysqlTransformRules"
                :is-zh="isZh"
                :transform-capabilities="mysqlTransformCapabilities"
              />

              <MysqlTargetConfigSection
                v-else
                v-model:target-config="mysqlTargetConfig"
                :is-zh="isZh"
              />
            </el-card>
          </el-col>

          <el-col :xs="24" :lg="24" :xl="6">
            <MysqlBuilderSidebar
              :connection-id="connectionId"
              :is-zh="isZh"
              :query-preview="generatedMysqlQuery"
              :summary-items="mysqlSummaryItems"
              :action-copy="actionCopy"
              :can-execute="canExecute"
              :executing="executing"
              :error-message="localError || jobError"
              @run="handleExecute"
              @apply="applyAiSuggestion"
            />
          </el-col>
        </el-row>
      </section>
    </template>

    <template v-else-if="type === 'REDIS'">
      <el-card shadow="never">
        <el-form label-position="top">
          <el-row :gutter="16">
            <el-col :xs="24">
              <el-form-item :label="t('trigger.keyPattern')">
                <el-input v-model="redisKeyPattern" placeholder="*" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="t('trigger.scanCount')">
                <el-input-number
                  v-model="redisScanCount"
                  :min="1"
                  controls-position="right"
                  class="full-width"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item :label="t('trigger.maxKeys')">
                <el-input-number
                  v-model="redisMaxKeys"
                  :min="1"
                  controls-position="right"
                  class="full-width"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>
    </template>

    <div v-if="type !== 'MYSQL'" class="action-row">
      <el-button
        type="primary"
        size="large"
        :loading="executing"
        :disabled="!canExecute"
        @click="handleExecute"
      >
        {{ executing ? t("trigger.launching") : t("trigger.button") }}
      </el-button>
      <span class="action-copy">{{ actionCopy }}</span>
    </div>

    <el-alert
      v-if="type !== 'MYSQL' && (localError || jobError)"
      :title="localError || jobError || ''"
      type="error"
      :closable="false"
      show-icon
    />
  </div>
</template>

<style scoped>
.trigger-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.full-width {
  width: 100%;
}

.builder-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.builder-head,
.action-row,
.stage-head,
.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.builder-title {
  margin: 0 0 4px;
  font-size: 20px;
  font-family: var(--font-display);
  letter-spacing: -0.03em;
}

.builder-copy,
.action-copy {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.builder-card {
  height: 100%;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: var(--radius-lg);
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.24);
  transition: border-color var(--duration-normal) var(--ease-out);
}

.builder-card:hover {
  border-color: rgba(255, 255, 255, 0.1);
}

.builder-card :deep(.el-card__header) {
  padding: 12px 16px;
}

.builder-card :deep(.el-card__body) {
  padding: 14px 16px;
}

.builder-step-card {
  background:
    linear-gradient(180deg, rgba(23, 40, 56, 0.96), rgba(17, 31, 45, 0.92)),
    rgba(15, 28, 41, 0.92);
}

.builder-stage {
  background:
    radial-gradient(
      circle at top right,
      rgba(123, 207, 255, 0.08),
      transparent 24%
    ),
    linear-gradient(180deg, rgba(12, 24, 36, 0.98), rgba(8, 18, 29, 0.96));
}

.builder-card h5 {
  margin: 2px 0 0;
  font-size: 15px;
  letter-spacing: -0.02em;
}

.card-kicker {
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.builder-stage :deep(.el-card__body) {
  min-height: 360px;
  padding: 14px 18px;
}

.stage-copy {
  min-width: 0;
  flex: 1;
}

@media (max-width: 1200px) {
  .builder-stage :deep(.el-card__body) {
    min-height: 0;
  }
}

@media (max-width: 720px) {
  .builder-head,
  .action-row,
  .stage-head,
  .card-head {
    flex-direction: column;
  }

  .action-row :deep(.el-button) {
    width: 100%;
  }
}
</style>
