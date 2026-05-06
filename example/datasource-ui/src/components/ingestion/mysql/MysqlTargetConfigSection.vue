<script setup lang="ts">
import { computed } from "vue";
import type { MysqlTargetConfig } from "@/types/mysqlBuilder";

const props = defineProps<{
  isZh: boolean;
}>();

const targetConfig = defineModel<MysqlTargetConfig>("targetConfig", {
  required: true,
});

const writeModeOptions: MysqlTargetConfig["writeMode"][] = [
  "APPEND",
  "UPSERT",
  "REPLACE",
];

const writeModeHint = computed(() => {
  return targetConfig.value.writeMode === "UPSERT"
    ? props.isZh
      ? "UPSERT 通常需要主键字段，否则无法稳定做合并更新。"
      : "UPSERT usually needs a primary key so merge-by-key stays deterministic."
    : targetConfig.value.writeMode === "REPLACE"
      ? props.isZh
        ? "REPLACE 适合整表重刷或覆盖式导入。"
        : "REPLACE fits truncate-and-reload or overwrite-style ingestion."
      : props.isZh
        ? "APPEND 适合只追加、不回写历史记录的场景。"
        : "APPEND fits insert-only loading without rewriting prior records.";
});
</script>

<template>
  <section class="section-shell">
    <div class="section-head">
      <div>
        <h5>{{ isZh ? "目标配置" : "Target Config" }}</h5>
        <p class="section-copy">
          {{
            isZh
              ? "定义目标表、写入模式以及主键或增量字段，约束最终落地方式。"
              : "Define the target name, write mode, and key fields that shape how the dataset lands."
          }}
        </p>
      </div>

      <el-tag type="warning" effect="dark">
        {{ isZh ? "落地策略" : "Load Strategy" }}
      </el-tag>
    </div>

    <el-form label-position="top">
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <el-form-item :label="isZh ? '目标名称' : 'Target Name'">
            <el-input
              v-model="targetConfig.targetName"
              :placeholder="isZh ? '例如 dwd_order' : 'e.g. dwd_order'"
            />
          </el-form-item>
        </el-col>

        <el-col :xs="24" :md="12">
          <el-form-item :label="isZh ? '写入模式' : 'Write Mode'">
            <el-select v-model="targetConfig.writeMode">
              <el-option
                v-for="mode in writeModeOptions"
                :key="mode"
                :label="mode"
                :value="mode"
              />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :xs="24" :md="12">
          <el-form-item :label="isZh ? '主键字段' : 'Primary Key'">
            <el-input
              v-model="targetConfig.primaryKey"
              :placeholder="
                isZh ? 'UPSERT 时建议填写' : 'Recommended for UPSERT'
              "
            />
          </el-form-item>
        </el-col>

        <el-col :xs="24" :md="12">
          <el-form-item :label="isZh ? '增量字段' : 'Incremental Field'">
            <el-input
              v-model="targetConfig.incrementalField"
              :placeholder="isZh ? '例如 updated_at' : 'e.g. updated_at'"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-alert :title="writeModeHint" type="info" :closable="false" show-icon />
  </section>
</template>

<style scoped>
.section-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
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

@media (max-width: 720px) {
  .section-head {
    flex-direction: column;
  }
}
</style>
