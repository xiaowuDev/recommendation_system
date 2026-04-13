import { ref } from 'vue'
import type { ConnectionTestResult } from '@/types/connection'
import { testSavedConnection, testTransientConnection } from '@/api/dataSource'
import { useLocale } from '@/composables/useLocale'

export function useConnectionTest() {
  const { t } = useLocale()
  const testResult = ref<ConnectionTestResult | null>(null)
  const testing = ref(false)
  const testError = ref<string | null>(null)

  async function testSaved(connectionId: number) {
    testing.value = true
    testResult.value = null
    testError.value = null
    try {
      testResult.value = await testSavedConnection(connectionId)
    } catch (e: any) {
      testError.value = e.message || t('detail.errorTest')
    } finally {
      testing.value = false
    }
  }

  async function testTransient(type: string, config: Record<string, unknown>) {
    testing.value = true
    testResult.value = null
    testError.value = null
    try {
      testResult.value = await testTransientConnection(type, config)
    } catch (e: any) {
      testError.value = e.message || t('detail.errorTest')
    } finally {
      testing.value = false
    }
  }

  return { testResult, testing, testError, testSaved, testTransient }
}
