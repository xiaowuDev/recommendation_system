<script setup lang="ts">
import type { MysqlBuilderTab, MysqlBuilderTabId } from '@/types/mysqlBuilder'

defineProps<{
  tabs: MysqlBuilderTab[]
  activeTab: MysqlBuilderTabId
}>()

const emit = defineEmits<{
  (e: 'select', tab: MysqlBuilderTabId): void
}>()
</script>

<template>
  <nav class="builder-tabs" aria-label="MySQL builder tabs">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      type="button"
      class="tab-chip"
      :class="{ 'tab-chip-active': tab.id === activeTab }"
      @click="emit('select', tab.id)"
    >
      <span class="tab-label">{{ tab.label }}</span>
      <span class="tab-meta">{{ tab.meta }}</span>
    </button>
  </nav>
</template>

<style scoped>
.builder-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.tab-chip {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-md);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.03), rgba(255, 255, 255, 0.02)),
    rgba(8, 18, 28, 0.72);
  color: var(--color-text-secondary);
  text-align: left;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out),
    background var(--duration-fast) var(--ease-out);
}

.tab-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(225, 191, 120, 0.24);
}

.tab-chip-active {
  border-color: rgba(225, 191, 120, 0.42);
  background:
    linear-gradient(135deg, rgba(225, 191, 120, 0.16), rgba(123, 207, 255, 0.06)),
    rgba(15, 28, 41, 0.92);
  box-shadow: var(--shadow-glow);
  color: var(--color-text);
}

.tab-label {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.tab-meta {
  font-size: 12px;
  color: inherit;
  opacity: 0.8;
}

@media (max-width: 1080px) {
  .builder-tabs {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .builder-tabs {
    grid-template-columns: 1fr;
  }
}
</style>
