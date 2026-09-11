import { http } from '@/utils/request'
import type { DashboardData } from '@/types'

/** 数据看板统计 */
export function getDashboard() {
  return http.get<DashboardData>('/admin/dashboard')
}
