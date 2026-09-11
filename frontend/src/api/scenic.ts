import http from '@/utils/request'
import { USE_MOCK } from '@/utils/constant'
import * as mock from '@/api/mock/handlers'

export interface ScenicItem {
  id: number
  name: string
  cover: string
  rating: number
  city: string
  category?: string
  price?: number
  openTime?: string
  description?: string
  images?: string[]
  reason?: string
  address?: string
  latitude?: number
  longitude?: number
  isCollected?: boolean
}

export interface ScenicQuery {
  keyword?: string
  city?: string
  category?: string
  sortBy?: 'rating' | 'price'
  page?: number
  pageSize?: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

/** 景点列表 — GET /scenic/list */
export function getScenicList(params: ScenicQuery) {
  if (USE_MOCK) return mock.mockGetScenicList(params)
  return http.get<PageResult<ScenicItem>>('/scenic/list', params as Record<string, unknown>)
}

/** 景点详情 — GET /scenic/:id */
export function getScenicDetail(id: number) {
  if (USE_MOCK) return mock.mockGetScenicDetail(id)
  return http.get<ScenicItem>(`/scenic/${id}`)
}

/** 热门景点 — GET /scenic/hot */
export function getHotScenics(limit = 10) {
  if (USE_MOCK) return mock.mockGetHotScenics(limit)
  return http.get<ScenicItem[]>('/scenic/hot', { limit })
}

/** 收藏景点 — POST /scenic/:id/collect */
export function collectScenic(id: number) {
  if (USE_MOCK) return mock.mockCollectScenic(id)
  return http.post<void>(`/scenic/${id}/collect`)
}

/** 取消收藏 — DELETE /scenic/:id/collect */
export function uncollectScenic(id: number) {
  if (USE_MOCK) return mock.mockUncollectScenic(id)
  return http.delete<void>(`/scenic/${id}/collect`)
}

/** 搜索景点 — GET /scenic/search */
export function searchScenic(keyword: string) {
  if (USE_MOCK) return mock.mockSearchScenic(keyword)
  return http.get<ScenicItem[]>('/scenic/search', { keyword })
}
