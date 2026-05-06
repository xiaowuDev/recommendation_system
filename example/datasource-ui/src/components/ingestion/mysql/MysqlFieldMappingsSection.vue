<script setup lang="ts">
import type { MysqlFieldMappingRule } from "@/types/mysqlBuilder";

defineProps<{
  isZh: boolean;
}>();

const fieldMappings = defineModel<MysqlFieldMappingRule[]>({ required: true });

function addFieldMapping() {
  fieldMappings.value.push({ sourceField: "", targetField: "", enabled: true });
}

function removeFieldMapping(index: number) {
  if (fieldMappings.value.length === 1) {
    return;
  }
  fieldMappings.value.splice(index, 1);
}
</script>

<template>
  <section class="section-shell">
    <div class="section-head">
      <div>
        <h5>{{ isZh ? "字段映射" : "Field Mapping" }}</h5>
        <p class="section-copy">
          {{
            isZh
              ? "像清单一样维护源字段与目标字段的对应关系。目标字段留空时会自动沿用源字段别名。"
              : "Maintain source-to-target field mappings as a compact list. Leave target blank to reuse the source alias."
          }}
        </p>
      </div>

      <el-button type="primary" plain @click="addFieldMapping">
        {{ isZh ? "新增字段" : "Add Field" }}
      </el-button>
    </div>

    <el-table :data="fieldMappings" size="small" class="mapping-table">
      <el-table-column :label="isZh ? '启用' : 'Enabled'" width="92">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" />
        </template>
      </el-table-column>

      <el-table-column
        :label="isZh ? '源字段' : 'Source Field'"
        min-width="220"
      >
        <template #default="{ row }">
          <el-input
            v-model="row.sourceField"
            :placeholder="isZh ? '例如 order_id' : 'e.g. order_id'"
          />
        </template>
      </el-table-column>

      <el-table-column
        :label="isZh ? '目标字段' : 'Target Field'"
        min-width="220"
      >
        <template #default="{ row }">
          <el-input
            v-model="row.targetField"
            :placeholder="
              isZh ? '留空时自动继承源字段别名' : 'Blank means source alias'
            "
          />
        </template>
      </el-table-column>

      <el-table-column width="100" align="right">
        <template #default="{ $index }">
          <el-button
            type="danger"
            link
            :disabled="fieldMappings.length === 1"
            @click="removeFieldMapping($index)"
          >
            {{ isZh ? "删除" : "Remove" }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
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

.mapping-table {
  width: 100%;
}

@media (max-width: 720px) {
  .section-head {
    flex-direction: column;
  }
}
</style>
