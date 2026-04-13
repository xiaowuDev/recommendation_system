<script setup lang="ts">
import type { DataSourceConnection } from '@/types/connection'
import { useLocale } from '@/composables/useLocale'

defineProps<{ connection: DataSourceConnection }>()
defineEmits<{ select: [id: number] }>()
const { t, localeCode } = useLocale()

function typeColor(type: string): string {
  switch (type) {
    case 'KAFKA': return 'var(--color-kafka)'
    case 'MYSQL': return 'var(--color-mysql)'
    case 'REDIS': return 'var(--color-redis)'
    case 'EXCEL': return 'var(--color-excel)'
    default: return 'var(--color-text-muted)'
  }
}

function formatDate(date: string): string {
  return new Date(date).toLocaleDateString(localeCode.value, {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  })
}
</script>

<template>
  <article
    class="conn-card"
    :style="{ '--type-accent': typeColor(connection.type) }"
    @click="$emit('select', connection.id)"
  >
    <div class="conn-card-header">
      <span class="conn-type-badge">
        {{ connection.type }}
      </span>
      <span class="conn-date">{{ t('card.added', { date: formatDate(connection.createdAt) }) }}</span>
    </div>
    <div class="conn-name">{{ connection.connectionName }}</div>
    <div class="conn-desc">
      {{ connection.description || t('card.defaultDesc') }}
    </div>
    <div class="conn-footer">
      <span class="conn-footnote">{{ t('card.maskedReady') }}</span>
      <span class="conn-cta">{{ t('card.inspect') }}</span>
    </div>
  </article>
</template>

<style scoped>
.conn-card {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at top right, color-mix(in srgb, var(--type-accent) 16%, transparent), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.05), rgba(255, 255, 255, 0.02)),
    var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: calc(var(--radius-lg) - 4px);
  padding: 18px;
  cursor: pointer;
  min-height: 210px;
  display: flex;
  flex-direction: column;
  transition:
    transform var(--duration-normal) var(--ease-out),
    border-color var(--duration-normal) var(--ease-out),
    box-shadow var(--duration-normal) var(--ease-out),
    background var(--duration-normal) var(--ease-out);
}

.conn-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.22), transparent);
}

.conn-card:hover {
  transform: translateY(-6px);
  border-color: color-mix(in srgb, var(--type-accent) 30%, rgba(225, 191, 120, 0.18));
  box-shadow: var(--shadow-md);
}

.conn-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.conn-type-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--type-accent) 14%, rgba(255, 255, 255, 0.04));
  border: 1px solid color-mix(in srgb, var(--type-accent) 28%, rgba(255, 255, 255, 0.08));
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--type-accent);
  text-transform: uppercase;
}

.conn-type-badge::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 12px color-mix(in srgb, var(--type-accent) 40%, transparent);
}

.conn-date {
  color: var(--color-text-muted);
  font-size: 11px;
}

.conn-name {
  font-size: 20px;
  line-height: 1.15;
  font-weight: 700;
  margin-bottom: 10px;
  letter-spacing: -0.03em;
}

.conn-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: auto;
  max-width: 34ch;
}

.conn-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.conn-footnote {
  color: var(--color-text-muted);
  font-size: 12px;
}

.conn-cta {
  color: var(--color-primary-hover);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
</style>
