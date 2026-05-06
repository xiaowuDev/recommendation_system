<script setup lang="ts">
import type { MysqlBuilderTab, MysqlBuilderTabId } from "@/types/mysqlBuilder";

defineProps<{
  steps: MysqlBuilderTab[];
  activeStep: MysqlBuilderTabId;
}>();

const emit = defineEmits<{
  (e: "select", step: MysqlBuilderTabId): void;
}>();

function handleSelect(step: string) {
  emit("select", step as MysqlBuilderTabId);
}
</script>

<template>
  <el-menu
    :default-active="activeStep"
    class="builder-menu"
    @select="handleSelect"
  >
    <el-menu-item
      v-for="(step, index) in steps"
      :key="step.id"
      :index="step.id"
      class="builder-menu-item"
    >
      <div class="step-content">
        <span class="step-index">{{ index + 1 }}</span>
        <div class="step-copy">
          <div class="step-copy-head">
            <strong>{{ step.label }}</strong>
            <span
              class="step-status"
              :class="`step-status-${step.status ?? 'todo'}`"
            >
              {{
                step.status === "done"
                  ? "Done"
                  : step.status === "active"
                    ? "Now"
                    : "Todo"
              }}
            </span>
          </div>
          <span>{{ step.meta }}</span>
        </div>
      </div>
    </el-menu-item>
  </el-menu>
</template>

<style scoped>
.builder-menu {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.builder-menu :deep(.el-menu-item) {
  height: auto;
  min-height: 56px;
  margin: 0;
  border-radius: var(--radius-sm);
  border: 1px solid rgba(160, 186, 209, 0.08);
  background: rgba(255, 255, 255, 0.02);
  line-height: 1.3;
  padding: 0 10px;
}

.builder-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.04);
}

.builder-menu :deep(.el-menu-item.is-active) {
  border-color: rgba(225, 191, 120, 0.28);
  background:
    linear-gradient(
      135deg,
      rgba(225, 191, 120, 0.16),
      rgba(123, 207, 255, 0.04)
    ),
    rgba(15, 28, 41, 0.92);
  box-shadow: var(--shadow-glow);
}

.step-content {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
}

.step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--color-text-secondary);
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.step-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.step-copy-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.step-copy strong {
  font-size: 12px;
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

.step-copy span {
  color: rgba(246, 239, 230, 0.72);
  font-size: 11px;
  white-space: normal;
  word-break: break-word;
}

.step-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 38px;
  padding: 2px 6px;
  border-radius: 999px;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  flex-shrink: 0;
}

.step-status-todo {
  color: var(--color-text-muted);
  background: rgba(255, 255, 255, 0.05);
}

.step-status-active {
  color: #fff6e0;
  background: rgba(225, 191, 120, 0.22);
}

.step-status-done {
  color: #def7ea;
  background: rgba(95, 208, 157, 0.18);
}
</style>
