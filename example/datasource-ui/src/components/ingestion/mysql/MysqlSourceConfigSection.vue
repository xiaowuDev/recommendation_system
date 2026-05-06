<script setup lang="ts">
import type { MysqlSourceConfig } from "@/types/mysqlBuilder";

defineProps<{
  isZh: boolean;
}>();

const sourceConfig = defineModel<MysqlSourceConfig>("sourceConfig", {
  required: true,
});
</script>

<template>
  <section class="section-shell">
    <div class="section-head">
      <div class="section-copy-block">
        <h5>{{ isZh ? "数据范围" : "Source Scope" }}</h5>
        <p class="section-copy">
          {{
            isZh
              ? "先确定读取哪张表、如何排序，以及这次是否直接切换到自定义 SQL。"
              : "Define the source table, row ordering, and whether this run should switch to custom SQL."
          }}
        </p>
      </div>
    </div>

    <el-radio-group
      :model-value="sourceConfig.useCustomQuery ? 'sql' : 'builder'"
      size="small"
      class="mode-switch"
      @update:model-value="sourceConfig.useCustomQuery = $event === 'sql'"
    >
      <el-radio-button label="builder">
        {{ isZh ? "规则构建" : "Rule Builder" }}
      </el-radio-button>
      <el-radio-button label="sql">
        {{ isZh ? "自定义 SQL" : "Custom SQL" }}
      </el-radio-button>
    </el-radio-group>

    <el-form label-position="top" class="section-form">
      <template v-if="!sourceConfig.useCustomQuery">
        <el-row :gutter="16">
          <el-col :xs="24" :lg="10">
            <el-form-item :label="isZh ? '源表' : 'Source Table'">
              <el-input
                v-model="sourceConfig.sourceTable"
                :placeholder="isZh ? '例如 ods_order' : 'e.g. ods_order'"
              />
            </el-form-item>
          </el-col>

          <el-col :xs="24" :md="8" :lg="6">
            <el-form-item :label="isZh ? '排序字段' : 'Sort Field'">
              <el-input
                v-model="sourceConfig.sortField"
                :placeholder="isZh ? '例如 updated_at' : 'e.g. updated_at'"
              />
            </el-form-item>
          </el-col>

          <el-col :xs="24" :md="8" :lg="4">
            <el-form-item :label="isZh ? '排序方向' : 'Sort Direction'">
              <el-select v-model="sourceConfig.sortDirection">
                <el-option label="DESC" value="DESC" />
                <el-option label="ASC" value="ASC" />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :xs="24" :md="8" :lg="4">
            <el-form-item :label="isZh ? '最大行数' : 'Max Rows'">
              <el-input-number
                v-model="sourceConfig.maxRows"
                :min="1"
                controls-position="right"
                class="full-width"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <template v-else>
        <el-form-item :label="isZh ? 'SQL 查询' : 'SQL Query'">
          <el-input
            v-model="sourceConfig.customQuery"
            type="textarea"
            :rows="10"
            :placeholder="'SELECT * FROM table LIMIT 100'"
          />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :xs="24" :md="8">
            <el-form-item :label="isZh ? '最大行数' : 'Max Rows'">
              <el-input-number
                v-model="sourceConfig.maxRows"
                :min="1"
                controls-position="right"
                class="full-width"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-alert
          :title="
            isZh
              ? '开启自定义 SQL 后，映射、过滤和转换仅作为参考，不会改写最终执行的查询。'
              : 'When custom SQL is enabled, mapping, filter, and transform settings remain references and do not rewrite the executed query.'
          "
          type="info"
          :closable="false"
          show-icon
        />
      </template>
    </el-form>
  </section>
</template>

<style scoped>
.section-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-head {
  display: block;
}

.section-head h5 {
  margin: 0 0 3px;
  font-size: 15px;
  letter-spacing: -0.02em;
}

.section-copy-block {
  min-width: 0;
}

.section-copy {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.mode-switch {
  width: fit-content;
}

.full-width {
  width: 100%;
}
</style>
