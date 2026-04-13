<script setup lang="ts">
import { ref, computed } from 'vue'
import { useIngestionJob } from '@/composables/useIngestionJob'
import { useLocale } from '@/composables/useLocale'

const props = defineProps<{ connectionId: number; type: string }>()
const { execute, executing, jobError } = useIngestionJob()
const { t } = useLocale()

const kafkaMaxRecords = ref(100)
const kafkaTimeout = ref(10000)

const mysqlQuery = ref('SELECT * FROM ')
const mysqlMaxRows = ref(1000)

const redisKeyPattern = ref('*')
const redisScanCount = ref(100)
const redisMaxKeys = ref(100)

const params = computed<Record<string, unknown>>(() => {
  switch (props.type) {
    case 'KAFKA':
      return { maxRecords: kafkaMaxRecords.value, timeoutMs: kafkaTimeout.value }
    case 'MYSQL':
      return { query: mysqlQuery.value, maxRows: mysqlMaxRows.value }
    case 'REDIS':
      return {
        keyPattern: redisKeyPattern.value,
        scanCount: redisScanCount.value,
        maxKeys: redisMaxKeys.value
      }
    default:
      return {}
  }
})

function handleExecute() {
  execute(props.connectionId, params.value)
}
</script>

<template>
  <div class="trigger-form">
    <template v-if="type === 'KAFKA'">
      <div class="param-grid">
        <div class="field-shell">
          <label class="label">{{ t('trigger.maxRecords') }}</label>
          <input v-model.number="kafkaMaxRecords" class="input" type="number" />
        </div>
        <div class="field-shell">
          <label class="label">{{ t('trigger.timeout') }}</label>
          <input v-model.number="kafkaTimeout" class="input" type="number" />
        </div>
      </div>
    </template>

    <template v-if="type === 'MYSQL'">
      <div class="field-shell">
        <label class="label">{{ t('trigger.sqlQuery') }}</label>
        <input v-model="mysqlQuery" class="input" :placeholder="t('trigger.sqlPlaceholder')" />
      </div>
      <div class="field-shell compact">
        <label class="label">{{ t('trigger.maxRows') }}</label>
        <input v-model.number="mysqlMaxRows" class="input" type="number" />
      </div>
    </template>

    <template v-if="type === 'REDIS'">
      <div class="field-shell">
        <label class="label">{{ t('trigger.keyPattern') }}</label>
        <input v-model="redisKeyPattern" class="input" placeholder="*" />
      </div>
      <div class="param-grid">
        <div class="field-shell">
          <label class="label">{{ t('trigger.scanCount') }}</label>
          <input v-model.number="redisScanCount" class="input" type="number" />
        </div>
        <div class="field-shell">
          <label class="label">{{ t('trigger.maxKeys') }}</label>
          <input v-model.number="redisMaxKeys" class="input" type="number" />
        </div>
      </div>
    </template>

    <div class="action-row">
      <button class="btn btn-primary" :disabled="executing" @click="handleExecute">
        {{ executing ? t('trigger.launching') : t('trigger.button') }}
      </button>
      <span class="action-copy">{{ t('trigger.actionCopy') }}</span>
    </div>

    <div v-if="jobError" class="trigger-error">{{ jobError }}</div>
  </div>
</template>

<style scoped>
.trigger-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.param-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field-shell.compact {
  max-width: 240px;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 6px;
}

.action-copy {
  color: var(--color-text-muted);
  font-size: 12px;
}

.trigger-error {
  padding: 14px 16px;
  background: rgba(255, 123, 123, 0.08);
  border-radius: var(--radius-md);
  border: 1px solid rgba(255, 123, 123, 0.2);
  color: #ffd7d7;
  font-size: 13px;
}

@media (max-width: 640px) {
  .param-grid,
  .action-row {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
