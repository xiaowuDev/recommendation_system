import { ref } from 'vue'
import type { IngestionJob } from '@/types/ingestion'
import { triggerIngestion, triggerExcelIngestion, fetchConnectionJobs } from '@/api/dataSource'
import { useLocale } from '@/composables/useLocale'

export function useIngestionJob() {
  const { t } = useLocale()
  const currentJob = ref<IngestionJob | null>(null)
  const recentJobs = ref<IngestionJob[]>([])
  const executing = ref(false)
  const jobError = ref<string | null>(null)

  async function execute(connectionId: number, params?: Record<string, unknown>) {
    executing.value = true
    currentJob.value = null
    jobError.value = null
    try {
      currentJob.value = await triggerIngestion(connectionId, params)
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
    } catch (e: any) {
      jobError.value = e.message || t('excel.errorRun')
    } finally {
      executing.value = false
    }
  }

  async function loadRecentJobs(connectionId: number, limit = 10) {
    try {
      recentJobs.value = await fetchConnectionJobs(connectionId, limit)
    } catch (e: any) {
      jobError.value = e.message || t('jobs.errorLoad')
    }
  }

  return { currentJob, recentJobs, executing, jobError, execute, executeExcel, loadRecentJobs }
}
