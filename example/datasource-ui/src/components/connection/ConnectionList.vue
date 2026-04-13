<script setup lang="ts">
import type { DataSourceConnection } from '@/types/connection'
import { useLocale } from '@/composables/useLocale'
import ConnectionCard from './ConnectionCard.vue'

defineProps<{ connections: DataSourceConnection[] }>()
defineEmits<{ select: [id: number] }>()
const { t } = useLocale()
</script>

<template>
  <div class="conn-list">
    <ConnectionCard
      v-for="conn in connections"
      :key="conn.id"
      :connection="conn"
      @select="$emit('select', $event)"
    />
    <div v-if="connections.length === 0" class="empty-panel empty">
      {{ t('card.listEmpty') }}
    </div>
  </div>
</template>

<style scoped>
.conn-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.empty {
  grid-column: 1 / -1;
  text-align: center;
  font-size: 14px;
}
</style>
