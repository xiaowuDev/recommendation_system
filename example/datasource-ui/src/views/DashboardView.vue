<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useConnections } from '@/composables/useConnections'
import { useLocale } from '@/composables/useLocale'
import ConnectionList from '@/components/connection/ConnectionList.vue'
import LoadingSpinner from '@/components/shared/LoadingSpinner.vue'

const router = useRouter()
const { connections, loading, error, loadConnections } = useConnections()
const { t } = useLocale()

onMounted(() => loadConnections())

function onSelect(id: number) {
  router.push(`/connections?id=${id}`)
}

const totalConnections = computed(() => connections.value.length)
const connectedTypes = computed(() => new Set(connections.value.map(conn => conn.type)).size)
const newestConnection = computed(() => {
  if (!connections.value.length) {
    return t('dashboard.noConnections')
  }

  const latest = [...connections.value]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0]

  return latest.connectionName
})

const typeHighlights = computed(() => [
  {
    label: t('dashboard.metrics.connections.label'),
    value: totalConnections.value,
    helper: totalConnections.value === 1
      ? t('dashboard.metrics.connections.one')
      : t('dashboard.metrics.connections.other')
  },
  {
    label: t('dashboard.metrics.types.label'),
    value: connectedTypes.value,
    helper: t('dashboard.metrics.types.helper')
  },
  {
    label: t('dashboard.metrics.ready.label'),
    value: connections.value.filter(conn => conn.type !== 'EXCEL').length,
    helper: t('dashboard.metrics.ready.helper')
  },
  {
    label: t('dashboard.metrics.newest.label'),
    value: newestConnection.value,
    helper: t('dashboard.metrics.newest.helper')
  }
])
</script>

<template>
  <div class="page-shell dashboard">
    <section class="hero-panel">
      <div class="hero-grid">
        <div class="hero-copy">
          <span class="section-eyebrow">{{ t('dashboard.eyebrow') }}</span>
          <h1 class="page-title hero-title">{{ t('dashboard.title') }}</h1>
          <p class="hero-description">{{ t('dashboard.description') }}</p>
          <div class="hero-actions">
            <button class="btn btn-primary" @click="router.push('/connections?new=1')">{{ t('dashboard.create') }}</button>
            <button class="btn" @click="router.push('/ingestion')">{{ t('dashboard.openIngestion') }}</button>
          </div>
        </div>

        <div class="metric-grid">
          <article
            v-for="item in typeHighlights"
            :key="item.label"
            class="metric-card"
          >
            <span class="metric-label">{{ item.label }}</span>
            <span class="metric-value">{{ item.value }}</span>
            <span class="metric-helper">{{ item.helper }}</span>
          </article>
        </div>
      </div>
    </section>

    <section class="surface-panel">
      <div class="panel-body">
        <div class="section-heading">
          <div>
            <h2 class="section-title">{{ t('dashboard.connectionsTitle') }}</h2>
            <p class="muted-copy">{{ t('dashboard.connectionsDesc') }}</p>
          </div>
          <span class="pill">{{ t('dashboard.tracked', { count: totalConnections }) }}</span>
        </div>

        <div class="library-content">
          <LoadingSpinner v-if="loading" :text="t('dashboard.loading')" />
          <div v-else-if="error" class="error-panel">{{ error }}</div>
          <ConnectionList v-else :connections="connections" @select="onSelect" />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dashboard {
  padding-top: 4px;
}

.hero-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(420px, 0.85fr);
  gap: 26px;
  align-items: stretch;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  margin-top: 6px;
}

.metric-helper {
  display: block;
  margin-top: 8px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.library-content {
  margin-top: 24px;
}

@media (max-width: 980px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .hero-actions {
    flex-direction: column;
  }
}
</style>
