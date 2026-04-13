<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { DataSourceType, CreateConnectionPayload } from '@/types/connection'
import { useLocale } from '@/composables/useLocale'
import { useConnectionTest } from '@/composables/useConnectionTest'
import { createConnection } from '@/api/dataSource'
import StatusBadge from '@/components/shared/StatusBadge.vue'
import JsonViewer from '@/components/shared/JsonViewer.vue'
import LoadingSpinner from '@/components/shared/LoadingSpinner.vue'

const emit = defineEmits<{ created: [] }>()
const { t, isZh } = useLocale()
const { testResult, testing, testError, testTransient } = useConnectionTest()

const name = ref('')
const description = ref('')
const type = ref<DataSourceType>('KAFKA')
const submitting = ref(false)
const error = ref<string | null>(null)

const kafkaServers = ref('localhost:9092')
const kafkaTopic = ref('')
const kafkaClientId = ref('')

const mysqlUrl = ref('jdbc:mysql://localhost:3306/mydb')
const mysqlUser = ref('root')
const mysqlPass = ref('')

const redisHost = ref('localhost')
const redisPort = ref(6379)
const redisDb = ref(0)
const redisPass = ref('')
const redisKeyPattern = ref('*')

const typeCopy = computed<Record<DataSourceType, { title: string; detail: string }>>(() => ({
  KAFKA: {
    title: t('form.type.kafka.title'),
    detail: t('form.type.kafka.detail')
  },
  MYSQL: {
    title: t('form.type.mysql.title'),
    detail: t('form.type.mysql.detail')
  },
  REDIS: {
    title: t('form.type.redis.title'),
    detail: t('form.type.redis.detail')
  },
  EXCEL: {
    title: t('form.type.excel.title'),
    detail: t('form.type.excel.detail')
  }
}))

const configPayload = computed<Record<string, unknown>>(() => {
  switch (type.value) {
    case 'KAFKA':
      return {
        bootstrapServers: kafkaServers.value,
        topic: kafkaTopic.value,
        ...(kafkaClientId.value ? { clientId: kafkaClientId.value } : {})
      }
    case 'MYSQL':
      return {
        jdbcUrl: mysqlUrl.value,
        username: mysqlUser.value,
        ...(mysqlPass.value ? { password: mysqlPass.value } : {})
      }
    case 'REDIS':
      return {
        host: redisHost.value,
        port: redisPort.value,
        database: redisDb.value,
        ...(redisPass.value ? { password: redisPass.value } : {}),
        keyPattern: redisKeyPattern.value
      }
    case 'EXCEL':
      return {}
  }
})

const testButtonLabel = computed(() => {
  if (type.value === 'EXCEL') {
    return ''
  }
  return testing.value
    ? (isZh.value ? '测试连接中...' : 'Testing Connection...')
    : (isZh.value ? '测试连接' : 'Test Connection')
})

const testHint = computed(() => isZh.value
  ? '保存前先验证连接是否可用。'
  : 'Validate the connection before saving.')

const testIdle = computed(() => isZh.value
  ? '填写连接配置后，可先点击测试连接验证目标是否可达。'
  : 'Fill in the connection settings, then run a transient test before saving.')

watch([type, configPayload], () => {
  testResult.value = null
  testError.value = null
}, { deep: true })

async function handleTestConnection() {
  if (type.value === 'EXCEL') {
    return
  }
  await testTransient(type.value, configPayload.value)
}

async function handleSubmit() {
  if (!name.value.trim()) {
    error.value = t('form.errorRequiredName')
    return
  }

  submitting.value = true
  error.value = null

  try {
    const payload: CreateConnectionPayload = {
      connectionName: name.value.trim(),
      description: description.value.trim() || undefined,
      type: type.value,
      config: configPayload.value
    }
    await createConnection(payload)
    emit('created')
    name.value = ''
    description.value = ''
    testResult.value = null
    testError.value = null
  } catch (e: any) {
    error.value = e.message || t('form.errorCreateFailed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="conn-form" @submit.prevent="handleSubmit">
    <div class="form-head">
      <div>
        <span class="section-eyebrow">{{ t('form.eyebrow') }}</span>
        <h3 class="form-title">{{ t('form.title') }}</h3>
        <p class="form-copy">{{ t('form.description') }}</p>
      </div>
      <div class="type-spotlight" :class="'spotlight-' + type.toLowerCase()">
        <span class="spotlight-label">{{ type }}</span>
        <strong>{{ typeCopy[type].title }}</strong>
        <span>{{ typeCopy[type].detail }}</span>
      </div>
    </div>

    <div class="form-grid">
      <div class="form-row">
        <label class="label">{{ t('form.connectionName') }}</label>
        <input v-model="name" class="input" :placeholder="t('form.placeholder.connectionName')" />
      </div>

      <div class="form-row">
        <label class="label">{{ t('form.sourceType') }}</label>
        <select v-model="type" class="select">
          <option value="KAFKA">KAFKA</option>
          <option value="MYSQL">MYSQL</option>
          <option value="REDIS">REDIS</option>
          <option value="EXCEL">EXCEL</option>
        </select>
      </div>
    </div>

    <div class="form-row">
      <label class="label">{{ t('form.descriptionLabel') }}</label>
      <input
        v-model="description"
        class="input"
        :placeholder="t('form.descriptionPlaceholder')"
      />
    </div>

    <section class="config-shell">
      <div class="config-header">
        <div>
          <h4>{{ t('form.configTitle') }}</h4>
          <p>{{ typeCopy[type].detail }}</p>
        </div>
        <span class="config-pill">{{ t('form.settings', { type }) }}</span>
      </div>

      <template v-if="type === 'KAFKA'">
        <div class="config-grid">
          <div class="form-row">
            <label class="label">{{ t('form.bootstrapServers') }}</label>
            <input v-model="kafkaServers" class="input" :placeholder="t('form.placeholder.hostPort')" />
          </div>
          <div class="form-row">
            <label class="label">{{ t('form.topic') }}</label>
            <input v-model="kafkaTopic" class="input" :placeholder="t('form.placeholder.topic')" />
          </div>
        </div>
        <div class="form-row">
          <label class="label">{{ t('form.clientId') }}</label>
          <input v-model="kafkaClientId" class="input" :placeholder="t('form.placeholder.clientId')" />
        </div>
      </template>

      <template v-if="type === 'MYSQL'">
        <div class="form-row">
          <label class="label">{{ t('form.jdbcUrl') }}</label>
          <input v-model="mysqlUrl" class="input" :placeholder="t('form.placeholder.jdbc')" />
        </div>
        <div class="config-grid">
          <div class="form-row">
            <label class="label">{{ t('form.username') }}</label>
            <input v-model="mysqlUser" class="input" :placeholder="t('form.placeholder.dbUser')" />
          </div>
          <div class="form-row">
            <label class="label">{{ t('form.password') }}</label>
            <input v-model="mysqlPass" class="input" type="password" :placeholder="t('form.placeholder.secret')" />
          </div>
        </div>
      </template>

      <template v-if="type === 'REDIS'">
        <div class="config-grid config-grid-redis">
          <div class="form-row">
            <label class="label">{{ t('form.host') }}</label>
            <input v-model="redisHost" class="input" :placeholder="t('form.placeholder.redisHost')" />
          </div>
          <div class="form-row">
            <label class="label">{{ t('form.port') }}</label>
            <input v-model.number="redisPort" class="input" type="number" />
          </div>
          <div class="form-row">
            <label class="label">{{ t('form.database') }}</label>
            <input v-model.number="redisDb" class="input" type="number" min="0" />
          </div>
        </div>
        <div class="config-grid">
          <div class="form-row">
            <label class="label">{{ t('form.password') }}</label>
            <input v-model="redisPass" class="input" type="password" :placeholder="t('form.placeholder.secret')" />
          </div>
          <div class="form-row">
            <label class="label">{{ t('form.keyPattern') }}</label>
            <input v-model="redisKeyPattern" class="input" :placeholder="t('form.placeholder.pattern')" />
          </div>
        </div>
      </template>

      <template v-if="type === 'EXCEL'">
        <div class="excel-note">
          {{ t('form.excelNote') }}
        </div>
      </template>
    </section>

    <div v-if="error" class="form-error">{{ error }}</div>

    <section v-if="type !== 'EXCEL'" class="test-shell">
      <div class="test-header">
        <div>
          <h4 class="test-title">{{ isZh ? '连接测试' : 'Connection Test' }}</h4>
          <p class="test-copy">{{ testHint }}</p>
        </div>
        <button type="button" class="btn" :disabled="testing" @click="handleTestConnection">
          {{ testButtonLabel }}
        </button>
      </div>

      <LoadingSpinner v-if="testing" :text="isZh ? '正在校验连接配置...' : 'Validating connection settings...'" />

      <div v-else-if="testResult" class="test-result" :class="testResult.success ? 'result-ok' : 'result-fail'">
        <div class="test-result-head">
          <StatusBadge :status="testResult.success ? 'SUCCESS' : 'FAILED'" />
          <strong>{{ testResult.message }}</strong>
        </div>
        <p class="test-result-time">
          {{ isZh ? '测试时间：' : 'Tested at: ' }}{{ new Date(testResult.testedAt).toLocaleString() }}
        </p>
        <JsonViewer v-if="Object.keys(testResult.detail || {}).length" :data="testResult.detail" />
      </div>

      <div v-else-if="testError" class="test-result result-fail">
        {{ testError }}
      </div>

      <div v-else class="test-idle">
        {{ testIdle }}
      </div>
    </section>

    <div class="form-footer">
      <p class="footer-copy">{{ t('form.footer') }}</p>
      <button type="submit" class="btn btn-primary" :disabled="submitting">
        {{ submitting ? t('form.creating') : t('form.create') }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.conn-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 20px;
  align-items: start;
}

.form-title {
  margin-top: 14px;
  font-family: var(--font-display);
  font-size: 38px;
  line-height: 1.02;
  letter-spacing: -0.05em;
}

.form-copy {
  margin-top: 12px;
  color: var(--color-text-secondary);
  max-width: 56ch;
}

.type-spotlight {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 20px;
  border-radius: var(--radius-lg);
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
}

.type-spotlight strong {
  font-size: 18px;
  letter-spacing: -0.03em;
}

.type-spotlight span:last-child {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.spotlight-label {
  color: var(--color-primary-hover);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.spotlight-kafka { box-shadow: inset 0 0 0 1px rgba(242, 138, 93, 0.08); }
.spotlight-mysql { box-shadow: inset 0 0 0 1px rgba(106, 190, 215, 0.08); }
.spotlight-redis { box-shadow: inset 0 0 0 1px rgba(255, 129, 110, 0.08); }
.spotlight-excel { box-shadow: inset 0 0 0 1px rgba(118, 211, 156, 0.08); }

.form-grid,
.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.config-grid-redis {
  grid-template-columns: 1.3fr 0.7fr 0.7fr;
}

.config-shell {
  padding: 24px;
  border-radius: var(--radius-lg);
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
}

.config-header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.config-header h4 {
  font-size: 20px;
  letter-spacing: -0.03em;
  margin-bottom: 4px;
}

.config-header p {
  color: var(--color-text-secondary);
  font-size: 13px;
  max-width: 56ch;
}

.config-pill {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: var(--color-text-secondary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.excel-note {
  padding: 16px 18px;
  border-radius: var(--radius-md);
  background: rgba(118, 211, 156, 0.08);
  border: 1px solid rgba(118, 211, 156, 0.18);
  color: var(--color-text-secondary);
}

.form-error {
  padding: 14px 16px;
  background: rgba(255, 123, 123, 0.08);
  border: 1px solid rgba(255, 123, 123, 0.25);
  border-radius: var(--radius-md);
  color: #ffd7d7;
  font-size: 13px;
}

.test-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
  border-radius: var(--radius-lg);
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
}

.test-header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
}

.test-title {
  font-size: 18px;
  letter-spacing: -0.03em;
  margin-bottom: 4px;
}

.test-copy {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.test-idle,
.test-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 13px;
}

.test-idle {
  color: var(--color-text-secondary);
  background: rgba(255, 255, 255, 0.03);
}

.test-result-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.test-result-time {
  color: var(--color-text-muted);
  font-size: 12px;
}

.result-ok {
  background: rgba(95, 208, 157, 0.1);
  border-color: rgba(95, 208, 157, 0.2);
}

.result-fail {
  background: rgba(255, 123, 123, 0.08);
  border-color: rgba(255, 123, 123, 0.2);
}

.form-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 8px;
}

.footer-copy {
  color: var(--color-text-muted);
  font-size: 13px;
  max-width: 52ch;
}

@media (max-width: 980px) {
  .form-head,
  .form-grid,
  .config-grid,
  .config-grid-redis,
  .form-footer {
    grid-template-columns: 1fr;
    display: grid;
  }

  .form-footer {
    gap: 12px;
  }
}

@media (max-width: 640px) {
  .form-title {
    font-size: 30px;
  }

  .config-shell {
    padding: 18px;
  }

  .config-header {
    flex-direction: column;
  }

  .test-header {
    flex-direction: column;
  }
}
</style>
