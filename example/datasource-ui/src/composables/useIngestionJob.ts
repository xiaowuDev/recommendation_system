import { ref } from 'vue'
import type { IngestionJob } from '@/types/ingestion'
import { triggerIngestion, triggerExcelIngestion, fetchConnectionJobs } from '@/api/dataSource'
import { useLocale } from '@/composables/useLocale'

const currentJob = ref<IngestionJob | null>(null)
const recentJobs = ref<IngestionJob[]>([])
const executing = ref(false)
const jobError = ref<string | null>(null)
const lastConnectionId = ref<number | null>(null)

export function useIngestionJob() {
  const { t } = useLocale()

  async function execute(connectionId: number, params?: Record<string, unknown>) {
    executing.value = true
    currentJob.value = null
    jobError.value = null
    lastConnectionId.value = connectionId
    try {
      currentJob.value = await triggerIngestion(connectionId, params)
      await loadRecentJobs(connectionId)
    } catch (e: any) {
      jobError.value = e.message || t('trigger.errorRun')
    } finally {
      executing.value = false
    }
  }

  async function executeExcel(file: File, params?: {
    sheetName?: string
    headerRowIndex?: number
    maxRows?: number
  }) {
    executing.value = true
    currentJob.value = null
    jobError.value = null
    try {
      currentJob.value = await triggerExcelIngestion(file, params)
      if (lastConnectionId.value != null) {
        await loadRecentJobs(lastConnectionId.value)
      }
    } catch (e: any) {
      jobError.value = e.message || t('excel.errorRun')
    } finally {
      executing.value = false
    }
  }

  async function loadRecentJobs(connectionId: number, limit = 10) {
    lastConnectionId.value = connectionId
    try {
      recentJobs.value = await fetchConnectionJobs(connectionId, limit)
    } catch (e: any) {
      jobError.value = e.message || t('jobs.errorLoad')
    }
  }

  return { currentJob, recentJobs, executing, jobError, execute, executeExcel, loadRecentJobs }
}
