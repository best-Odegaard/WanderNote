import { http } from '@/utils/request'

/** 精选行程（首页轮播展示的完整行程） */
export interface FeaturedTrip {
  id: number
  title: string
  subtitle?: string
  city?: string
  days?: number
  cover?: string
  /** 完整行程 JSON（逐日明细） */
  tripJson: string
  sourceUrl?: string
  sortOrder?: number
  status?: number
}

export interface FeaturedTripSaveParams {
  id?: number
  title: string
  subtitle?: string
  city?: string
  days?: number
  cover?: string
  tripJson: string
  sourceUrl?: string
  sortOrder?: number
  status?: number
  /** 解析链接时后端自动抓到的网页正文，只在「解析生成行程」的返回里带 */
  sourceText?: string
}

export function getFeaturedList() {
  return http.get<FeaturedTrip[]>('/admin/featured/list')
}

export function addFeatured(data: FeaturedTripSaveParams) {
  return http.post<void>('/admin/featured', data)
}

export function updateFeatured(data: FeaturedTripSaveParams) {
  return http.put<void>('/admin/featured', data)
}

export function deleteFeatured(id: number) {
  return http.delete<void>(`/admin/featured/${id}`)
}

export function updateFeaturedStatus(id: number, status: number) {
  return http.put<void>(`/admin/featured/${id}/status/${status}`)
}

export interface FeaturedParseParams {
  /** 外部游记链接（可空，仅作溯源） */
  sourceUrl?: string
  /** 目的地城市；多数分享链接里没有中文城市名，需要手填 */
  city?: string
  /** 行程原文：填了就让 AI 按原文生成，而不是凭空推荐 */
  content?: string
}

/**
 * 解析外部内容生成行程草稿（不落库）。
 * - 只填城市 → AI 按城市生成
 * - 填了「行程原文」→ AI 按原文生成（保留原文景点与顺序）
 * 生成结果填进行程 JSON，可再手工编辑。
 */
export function parseFeaturedLink(params: FeaturedParseParams) {
  // AI 生成分两步（先出日程框架、再补每段明细），实测 6~7 分钟；智能体忙的时候更久。
  // 这里单独放开，否则请求会在客户端被掐断，界面上点完没有任何结果。
  return http.post<FeaturedTripSaveParams>('/admin/featured/parse-link', params, {
    timeout: 1500000
  })
}
