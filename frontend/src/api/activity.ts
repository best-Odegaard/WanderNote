import http from '@/utils/request'
import { USE_MOCK } from '@/utils/constant'
import * as mock from '@/api/mock/handlers'

export interface ActivityItem {
  id: number
  title: string
  cover: string
  startTime: string
  endTime?: string
  location: string
  city: string
  category?: string
  organizer?: string
  description?: string
  images?: string[]
  price?: number
  isCollected?: boolean
}

export interface ActivityQuery {
  city?: string
  category?: string
  startDate?: string
  endDate?: string
  page?: number
  pageSize?: number
}

export interface PageResult<T> {
  records: T[]
  total: number
}

/** 活动列表 — GET /activity/list */
export function getActivityList(params: ActivityQuery) {
  if (USE_MOCK) return mock.mockGetActivityList(params)
  return http.get<PageResult<ActivityItem>>('/activity/list', params as Record<string, unknown>)
}

/** 活动详情 — GET /activity/:id */
export function getActivityDetail(id: number) {
  if (USE_MOCK) return mock.mockGetActivityDetail(id)
  return http.get<ActivityItem>(`/activity/${id}`)
}

/** 热门活动 — GET /activity/hot */
export function getHotActivities(limit = 10) {
  if (USE_MOCK) return mock.mockGetHotActivities(limit)
  return http.get<ActivityItem[]>('/activity/hot', { limit })
}

/** 收藏活动 — POST /activity/:id/collect */
export function collectActivity(id: number) {
  if (USE_MOCK) return mock.mockCollectActivity(id)
  return http.post<void>(`/activity/${id}/collect`)
}

/** 取消收藏 — DELETE /activity/:id/collect */
export function uncollectActivity(id: number) {
  if (USE_MOCK) return mock.mockUncollectActivity(id)
  return http.delete<void>(`/activity/${id}/collect`)
}

/** 活动报名 — POST /activity/:id/enroll */
export function enrollActivity(id: number) {
  if (USE_MOCK) return mock.mockEnrollActivity(id)
  return http.post<void>(`/activity/${id}/enroll`, {}, { showLoading: true })
}
