<script setup lang="ts">
import { computed } from 'vue'
import { useLocale } from '@/composables/useLocale'

const props = defineProps<{ status: string }>()
const { t } = useLocale()

function statusClass(status: string): string {
  switch (status.toUpperCase()) {
    case 'COMPLETED': case 'SUCCESS': return 'badge-success'
    case 'FAILED': case 'BLOCKED': return 'badge-error'
    case 'RUNNING': case 'PENDING': return 'badge-warning'
    default: return 'badge-info'
  }
}

const label = computed(() => t(`status.${props.status.toUpperCase()}`))
</script>

<template>
  <span class="badge" :class="statusClass(status)">{{ label }}</span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  border: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(16px);
}

.badge::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 10px currentColor;
}

.badge-success { background: rgba(95,208,157,.14); color: var(--color-success); }
.badge-error { background: rgba(255,123,123,.14); color: var(--color-error); }
.badge-warning { background: rgba(255,193,115,.14); color: var(--color-warning); }
.badge-info { background: rgba(123,207,255,.14); color: var(--color-info); }
</style>
