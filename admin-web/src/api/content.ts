import { http, download } from '@/utils/request'
import type { PageResult, ScenicItem, ActivityItem, ImportResult } from '@/types'

export interface ContentQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  city?: string
  category?: string
  status?: number
}

/** 景点保存参数 */
export interface ScenicSaveParams {
  id?: number
  name: string
  cover?: string
  city?: string
  rating?: number
  category?: string
  price?: number
  openTime?: string
  description?: string
  address?: string
  images?: string[]
  isHot?: number
  sortOrder?: number
  status?: number
}

/** 活动保存参数 */
export interface ActivitySaveParams {
  id?: number
  title: string
  cover?: string
  city?: string
  location?: string
  category?: string
  startTime?: string
  endTime?: string
  description?: string
  isHot?: number
  enrollCount?: number
  sortOrder?: number
  status?: number
}

// ── 景点 ──

export function getScenicList(params: ContentQuery) {
  return http.get<PageResult<ScenicItem>>('/admin/content/scenic', params as unknown as Record<string, unknown>)
}

export function addScenic(data: ScenicSaveParams) {
  return http.post<void>('/admin/content/scenic', data)
}

export function updateScenic(data: ScenicSaveParams) {
  return http.put<void>('/admin/content/scenic', data)
}

export function deleteScenic(id: number) {
  return http.delete<void>(`/admin/content/scenic/${id}`)
}

export function updateScenicStatus(id: number, status: number) {
  return http.put<void>(`/admin/content/scenic/${id}/status/${status}`)
}

/** Excel 批量导入景点（返回成功/失败行明细） */
export function importScenicExcel(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  // 不手动设置 Content-Type，axios 自动带 boundary
  return http.post<ImportResult>('/admin/content/scenic/import', formData)
}

/** 下载景点导入模板（xlsx） */
export function downloadScenicTemplate() {
  return download('/admin/content/scenic/template', '景点导入模板.xlsx')
}

// ── 活动 ──

export function getActivityList(params: ContentQuery) {
  return http.get<PageResult<ActivityItem>>('/admin/content/activity', params as unknown as Record<string, unknown>)
}

export function addActivity(data: ActivitySaveParams) {
  return http.post<void>('/admin/content/activity', data)
}

export function updateActivity(data: ActivitySaveParams) {
  return http.put<void>('/admin/content/activity', data)
}

export function deleteActivity(id: number) {
  return http.delete<void>(`/admin/content/activity/${id}`)
}

export function updateActivityStatus(id: number, status: number) {
  return http.put<void>(`/admin/content/activity/${id}/status/${status}`)
}
