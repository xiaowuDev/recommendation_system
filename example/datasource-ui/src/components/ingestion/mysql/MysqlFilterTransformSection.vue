<script setup lang="ts">
import { ref } from "vue";
import type { MysqlTransformRuleCapability } from "@/types/ingestion";
import type {
  MysqlFilterOperator,
  MysqlFilterRule,
  MysqlTransformRule,
} from "@/types/mysqlBuilder";

const props = defineProps<{
  isZh: boolean;
  transformCapabilities: MysqlTransformRuleCapability[];
}>();

const filters = defineModel<MysqlFilterRule[]>("filters", { required: true });
const transformRules = defineModel<MysqlTransformRule[]>("transformRules", {
  required: true,
});

const activePanel = ref<"filters" | "transforms">("filters");

const filterOperatorOptions: MysqlFilterOperator[] = [
  "EQ",
  "NE",
  "GT",
  "GTE",
  "LT",
  "LTE",
  "LIKE",
  "IN",
  "IS_NULL",
  "NOT_NULL",
];

function resolveDefaultTransformType(): string {
  return props.transformCapabilities[0]?.code ?? "TRIM";
}

function addFilterRule() {
  filters.value.push({
    logic: "AND",
    field: "",
    operator: "EQ",
    value: "",
    enabled: true,
  });
}

function removeFilterRule(index: number) {
  filters.value.splice(index, 1);
}

function addTransformRule() {
  transformRules.value.push({
    field: "",
    targetField: "",
    transformType: resolveDefaultTransformType(),
    argument: "",
    enabled: true,
  });
}

function removeTransformRule(index: number) {
  transformRules.value.splice(index, 1);
}

function transformArgumentPlaceholder(transformType: string): string {
  const capability = props.transformCapabilities.find(
    (rule) => rule.code === transformType,
  );
  if (capability?.argumentHint) {
    return capability.argumentHint;
  }
  return props.isZh ? "规则参数" : "Rule argument";
}
</script>

<template>
  <section class="section-shell">
    <div class="section-head">
      <div>
        <h5>{{ isZh ? "过滤与转换" : "Filter + Transform" }}</h5>
        <p class="section-copy">
          {{
            isZh
              ? "过滤先缩小数据范围，转换再清洗和改写字段。用页签分开处理，避免一屏过长。"
              : "Filters narrow the dataset first, then transforms clean and reshape fields. Each stays in its own compact tab."
          }}
        </p>
      </div>
    </div>

    <el-tabs v-model="activePanel" class="transform-tabs">
      <el-tab-pane :label="isZh ? '过滤条件' : 'Filters'" name="filters">
        <div class="tab-head">
          <el-text class="tab-copy">
            {{
              isZh
                ? "支持空值判断、IN 列表以及多条件 AND / OR 组合。"
                : "Support null checks, IN lists, and chained AND / OR conditions."
            }}
          </el-text>
          <el-button type="primary" plain @click="addFilterRule">
            {{ isZh ? "新增过滤" : "Add Filter" }}
          </el-button>
        </div>

        <el-empty
          v-if="filters.length === 0"
          :description="
            isZh
              ? '当前没有过滤条件，本次将读取源范围内的全部记录。'
              : 'No filters yet. The run will read every row in the current source scope.'
          "
        />

        <el-table v-else :data="filters" size="small" class="rule-table">
          <el-table-column :label="isZh ? '启用' : 'Enabled'" width="92">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" />
            </template>
          </el-table-column>

          <el-table-column :label="isZh ? '逻辑' : 'Logic'" width="120">
            <template #default="{ row, $index }">
              <el-select v-model="row.logic" :disabled="$index === 0">
                <el-option label="AND" value="AND" />
                <el-option label="OR" value="OR" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column :label="isZh ? '字段' : 'Field'" min-width="180">
            <template #default="{ row }">
              <el-input
                v-model="row.field"
                :placeholder="isZh ? '例如 status' : 'e.g. status'"
              />
            </template>
          </el-table-column>

          <el-table-column :label="isZh ? '操作符' : 'Operator'" width="150">
            <template #default="{ row }">
              <el-select v-model="row.operator">
                <el-option
                  v-for="operator in filterOperatorOptions"
                  :key="operator"
                  :label="operator"
                  :value="operator"
                />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column :label="isZh ? '值' : 'Value'" min-width="220">
            <template #default="{ row }">
              <el-input
                v-model="row.value"
                :disabled="
                  row.operator === 'IS_NULL' || row.operator === 'NOT_NULL'
                "
                :placeholder="
                  row.operator === 'IN'
                    ? isZh
                      ? '例如 1,2,3'
                      : 'e.g. 1,2,3'
                    : isZh
                      ? '过滤值'
                      : 'Filter value'
                "
              />
            </template>
          </el-table-column>

          <el-table-column width="100" align="right">
            <template #default="{ $index }">
              <el-button type="danger" link @click="removeFilterRule($index)">
                {{ isZh ? "删除" : "Remove" }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="isZh ? '转换规则' : 'Transforms'" name="transforms">
        <div class="tab-head">
          <el-text class="tab-copy">
            {{
              isZh
                ? "保持输入字段和输出字段可见，便于快速校对清洗链路。"
                : "Keep input and output fields visible so the transform chain stays easy to verify."
            }}
          </el-text>
          <el-button type="primary" plain @click="addTransformRule">
            {{ isZh ? "新增转换" : "Add Transform" }}
          </el-button>
        </div>

        <el-empty
          v-if="transformRules.length === 0"
          :description="
            isZh
              ? '当前没有转换规则，字段会按映射结果直接输出。'
              : 'No transforms yet. Mapped fields will pass through as-is.'
          "
        />

        <el-table v-else :data="transformRules" size="small" class="rule-table">
          <el-table-column :label="isZh ? '启用' : 'Enabled'" width="92">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" />
            </template>
          </el-table-column>

          <el-table-column
            :label="isZh ? '输入字段' : 'Input Field'"
            min-width="180"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.field"
                :placeholder="isZh ? '例如 order_name' : 'e.g. order_name'"
              />
            </template>
          </el-table-column>

          <el-table-column
            :label="isZh ? '转换类型' : 'Transform Type'"
            width="180"
          >
            <template #default="{ row }">
              <el-select v-model="row.transformType">
                <el-option
                  v-for="transformType in transformCapabilities"
                  :key="transformType.code"
                  :label="transformType.code"
                  :value="transformType.code"
                />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column
            :label="isZh ? '输出字段' : 'Output Field'"
            min-width="180"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.targetField"
                :placeholder="
                  isZh
                    ? '留空则覆盖输入字段'
                    : 'Blank means overwrite input field'
                "
              />
            </template>
          </el-table-column>

          <el-table-column :label="isZh ? '参数' : 'Argument'" min-width="220">
            <template #default="{ row }">
              <el-input
                v-model="row.argument"
                :placeholder="transformArgumentPlaceholder(row.transformType)"
              />
            </template>
          </el-table-column>

          <el-table-column width="100" align="right">
            <template #default="{ $index }">
              <el-button
                type="danger"
                link
                @click="removeTransformRule($index)"
              >
                {{ isZh ? "删除" : "Remove" }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<style scoped>
.section-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-head h5 {
  margin: 0 0 3px;
  font-size: 15px;
  letter-spacing: -0.02em;
}

.section-copy {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.tab-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.tab-copy {
  color: var(--color-text-muted);
  line-height: 1.6;
}

@media (max-width: 720px) {
  .tab-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
