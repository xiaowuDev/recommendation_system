<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useConnections } from "@/composables/useConnections";
import { useLocale } from "@/composables/useLocale";
import { fetchConnection } from "@/api/dataSource";
import type { DataSourceConnection } from "@/types/connection";
import ConnectionDetail from "@/components/connection/ConnectionDetail.vue";
import ConnectionForm from "@/components/connection/ConnectionForm.vue";
import LoadingSpinner from "@/components/shared/LoadingSpinner.vue";

const route = useRoute();
const router = useRouter();
const { connections, loading, error, loadConnections } = useConnections();
const { t, localeCode } = useLocale();
const selectedConnection = ref<DataSourceConnection | null>(null);
const showForm = ref(false);

const selectedId = computed(() => {
  const id = route.query.id;
  return id ? Number(id) : null;
});

const typeSummary = computed(() => [
  {
    label: t("connections.metric.kafka"),
    value: connections.value.filter((conn) => conn.type === "KAFKA").length,
  },
  {
    label: t("connections.metric.mysql"),
    value: connections.value.filter((conn) => conn.type === "MYSQL").length,
  },
  {
    label: t("connections.metric.redis"),
    value: connections.value.filter((conn) => conn.type === "REDIS").length,
  },
  {
    label: t("connections.metric.excel"),
    value: connections.value.filter((conn) => conn.type === "EXCEL").length,
  },
]);

onMounted(async () => {
  await loadConnections();
  if (route.query.new === "1") showForm.value = true;
  if (selectedId.value) await selectConnection(selectedId.value);
});

watch(selectedId, (id) => {
  if (id) selectConnection(id);
});

async function selectConnection(id: number) {
  try {
    await router.replace({ query: { id: String(id) } });
    selectedConnection.value = await fetchConnection(id);
    showForm.value = false;
  } catch {
    selectedConnection.value = null;
  }
}

function onCreated() {
  showForm.value = false;
  loadConnections();
}

function toggleForm() {
  showForm.value = !showForm.value;
  if (showForm.value) {
    selectedConnection.value = null;
    router.replace({ query: { new: "1" } });
    return;
  }

  if (selectedConnection.value) {
    router.replace({ query: { id: String(selectedConnection.value.id) } });
    return;
  }

  router.replace({ query: {} });
}
</script>

<template>
  <div class="page-shell connections-page">
    <!-- Compact header bar with inline metrics -->
    <section class="hero-bar">
      <div class="hero-bar-left">
        <span class="section-eyebrow">{{ t("connections.eyebrow") }}</span>
        <h1 class="bar-title">{{ t("connections.title") }}</h1>
      </div>
      <div class="hero-bar-metrics">
        <span v-for="item in typeSummary" :key="item.label" class="metric-chip">
          <span class="metric-chip-value">{{ item.value }}</span>
          <span class="metric-chip-label">{{ item.label }}</span>
        </span>
      </div>
      <button class="btn btn-primary btn-sm" @click="toggleForm">
        {{ showForm ? t("connections.closeCreator") : t("connections.new") }}
      </button>
    </section>

    <div class="workspace-grid">
      <aside class="conn-sidebar surface-panel">
        <div class="panel-body sidebar-body">
          <div class="sidebar-header">
            <div>
              <h2 class="sidebar-title">{{ t("connections.sidebarTitle") }}</h2>
              <p class="sidebar-copy">
                {{
                  t("connections.sidebarAvailable", {
                    count: connections.length,
                  })
                }}
              </p>
            </div>
            <button class="btn btn-sm" @click="toggleForm">
              {{
                showForm ? t("connections.cancel") : `+ ${t("connections.new")}`
              }}
            </button>
          </div>

          <LoadingSpinner v-if="loading" :text="t('connections.loading')" />
          <div v-else-if="error" class="error-panel">{{ error }}</div>
          <div v-else-if="connections.length === 0" class="empty-panel">
            {{ t("connections.emptyLibrary") }}
          </div>
          <div v-else class="sidebar-list">
            <button
              v-for="conn in connections"
              :key="conn.id"
              class="sidebar-item"
              :class="{ active: selectedConnection?.id === conn.id }"
              @click="selectConnection(conn.id)"
            >
              <span class="item-top">
                <span
                  class="item-type"
                  :class="'type-' + conn.type.toLowerCase()"
                  >{{ conn.type }}</span
                >
                <span class="item-date">{{
                  new Date(conn.createdAt).toLocaleDateString(localeCode)
                }}</span>
              </span>
              <span class="item-name">{{ conn.connectionName }}</span>
              <span class="item-desc">
                {{ conn.description || t("card.defaultDesc") }}
              </span>
            </button>
          </div>
        </div>
      </aside>

      <section class="conn-main surface-panel">
        <div class="panel-body main-body">
          <ConnectionForm v-if="showForm" @created="onCreated" />
          <ConnectionDetail
            v-else-if="selectedConnection"
            :connection="selectedConnection"
          />
          <div v-else class="empty-state">
            <span class="empty-kicker">{{
              t("connections.emptyWorkspaceKicker")
            }}</span>
            <h3>{{ t("connections.emptyWorkspaceTitle") }}</h3>
            <p>{{ t("connections.emptyWorkspaceDesc") }}</p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.connections-page {
  padding-top: 4px;
}

/* ── Compact hero bar ── */
.hero-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 20px;
  border: 1px solid rgba(225, 191, 120, 0.14);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(circle at right, rgba(225, 191, 120, 0.1), transparent 40%),
    linear-gradient(160deg, rgba(13, 28, 42, 0.94), rgba(8, 16, 24, 0.86));
}

.hero-bar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.bar-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.03em;
  white-space: nowrap;
}

.hero-bar-metrics {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: center;
}

.metric-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
  font-size: 12px;
}

.metric-chip-value {
  font-weight: 700;
  color: var(--color-text);
}

.metric-chip-label {
  color: var(--color-text-muted);
  font-size: 11px;
}

/* ── Workspace grid ── */
.workspace-grid {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.sidebar-body,
.main-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sidebar-title {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.sidebar-copy {
  color: var(--color-text-secondary);
  font-size: 11px;
}

.sidebar-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sidebar-item {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 3px;
  width: 100%;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(255, 255, 255, 0.02);
  cursor: pointer;
  text-align: left;
  transition:
    transform var(--duration-normal) var(--ease-out),
    border-color var(--duration-normal) var(--ease-out),
    background var(--duration-normal) var(--ease-out),
    box-shadow var(--duration-normal) var(--ease-out);
}

.sidebar-item:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.12);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.sidebar-item.active {
  background:
    linear-gradient(
      135deg,
      rgba(225, 191, 120, 0.12),
      rgba(123, 207, 255, 0.05)
    ),
    rgba(225, 191, 120, 0.03);
  border-color: rgba(225, 191, 120, 0.28);
}

.item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.item-type {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
  min-width: 42px;
  text-transform: uppercase;
}

.item-date {
  color: var(--color-text-muted);
  font-size: 10px;
}

.type-kafka {
  color: var(--color-kafka);
}
.type-mysql {
  color: var(--color-mysql);
}
.type-redis {
  color: var(--color-redis);
}
.type-excel {
  color: var(--color-excel);
}

.item-name {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: -0.02em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-desc {
  color: var(--color-text-secondary);
  font-size: 11px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.conn-main {
  min-height: 480px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: start;
  min-height: 320px;
  border-radius: var(--radius-lg);
  border: 1px dashed rgba(255, 255, 255, 0.1);
  padding: 28px;
  background: rgba(255, 255, 255, 0.02);
}

.empty-kicker {
  color: var(--color-primary-hover);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  margin-bottom: 10px;
}

.empty-state h3 {
  font-family: var(--font-display);
  font-size: 26px;
  line-height: 1.1;
  letter-spacing: -0.04em;
  max-width: 16ch;
  margin-bottom: 8px;
}

.empty-state p {
  color: var(--color-text-secondary);
  max-width: 48ch;
}

@media (max-width: 1100px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero-bar {
    flex-wrap: wrap;
  }

  .hero-bar-metrics {
    order: 3;
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .sidebar-header {
    flex-direction: column;
    align-items: start;
  }

  .empty-state {
    min-height: 240px;
    padding: 20px;
  }

  .empty-state h3 {
    font-size: 22px;
  }
}
</style>
