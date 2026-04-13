import { ref } from 'vue'
import type { DataSourceConnection, SupportedType } from '@/types/connection'
import { fetchConnections, fetchSupportedTypes } from '@/api/dataSource'
import { useLocale } from '@/composables/useLocale'

const connections = ref<DataSourceConnection[]>([])
const supportedTypes = ref<SupportedType[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

export function useConnections() {
  const { t } = useLocale()

  async function loadConnections() {
    loading.value = true
    error.value = null
    try {
      connections.value = await fetchConnections()
    } catch (e: any) {
      error.value = e.message || t('connections.errorLoad')
    } finally {
      loading.value = false
    }
  }

  async function loadSupportedTypes() {
    try {
      supportedTypes.value = await fetchSupportedTypes()
    } catch (e: any) {
      error.value = e.message || t('connections.errorTypes')
    }
  }

  return {
    connections,
    supportedTypes,
    loading,
    error,
    loadConnections,
    loadSupportedTypes
  }
}
