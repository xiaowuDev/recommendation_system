import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'
import ConnectionsView from '@/views/ConnectionsView.vue'
import IngestionView from '@/views/IngestionView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: DashboardView },
    { path: '/connections', component: ConnectionsView },
    { path: '/ingestion', component: IngestionView }
  ]
})

export default router
