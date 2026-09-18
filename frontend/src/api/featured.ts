/**
 * 精选行程接口
 * 首页轮播展示的「一整个城市的完整行程」，点进去可查看、添加到我的行程、或对话修改后再加入。
 */
import http from '@/utils/request'
import type { TripPlan } from '@/api/trip'

export interface FeaturedTripItem {
  id: number
  title: string
  subtitle?: string
  city?: string
  days?: number
  cover?: string
  sourceUrl?: string
  sortOrder?: number
  status?: number
}

export interface FeaturedTripDetail extends FeaturedTripItem {
  /** 完整行程；后端由内嵌 JSON 解析而来，可直接当作 TripPlan 使用 */
  trip?: TripPlan
}

/** 精选行程列表 — GET /featured/list */
export function getFeaturedList() {
  return http.get<FeaturedTripItem[]>('/featured/list')
}

/** 精选行程详情（含完整行程）— GET /featured/:id */
export function getFeaturedDetail(id: number | string) {
  return http.get<FeaturedTripDetail>(`/featured/${id}`)
}

/**
 * 添加到我的行程（复制一份独立副本）— POST /featured/:id/copy?startDate=yyyy-MM-dd
 *
 * startDate：用户选定的出发日期。精选行程是「几天怎么玩」的模板，本身不带具体日期，
 * 出发日必须由用户此刻指定，后端再按天数推算返回日。
 * 不传时后端按「今天出发」兜底（兼容未升级的旧版本）。
 */
export function copyFeaturedToMine(id: number | string, startDate?: string) {
  const query = startDate ? `?startDate=${encodeURIComponent(startDate)}` : ''
  return http.post<TripPlan>(`/featured/${id}/copy${query}`, undefined, {
    showLoading: true,
    loadingText: '正在添加...'
  })
}
