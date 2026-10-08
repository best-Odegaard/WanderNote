/**
 * 酒店（住宿）接口。
 *
 * 边界：本站只做「候选筛选 + 跳转携程深链」，不做站内下单。
 *   携程没有公开开放 API，price 是**参考价**，接口里没有库存/房态字段 ——
 *   展示时必须带「参考价，以携程为准」，禁止出现「可订」「有房」字样。
 */
import http from '@/utils/request'

/** 后端 HotelVO 的结构 */
export interface HotelItem {
  /** 业务编码（hotel_candidate.hotel_code）；兜底酒店没有编码 */
  id?: string
  name: string
  city?: string
  /** 经济型 / 舒适型 / 高档型 / 特色民宿 */
  level?: string
  rating?: number
  /** 参考价（元/晚），以携程为准 */
  price?: number
  address?: string
  /** 附近地标（深链关键词 + 位置筛选） */
  nearbyLandmark?: string
  distanceKm?: number
  tags?: string[]
  cover?: string
  lng?: number
  lat?: number
  desc?: string
  /** 携程深链；为空表示后端没配模板，前端应隐藏按钮而不是给死链 */
  ctripUrl?: string
  // 仅 GET /hotel/trip/{tripId} 会填
  tripId?: number
  checkin?: string
  checkout?: string
}

export interface HotelSearchParams {
  city?: string
  /** YYYY-MM-DD，仅用于拼携程深链 */
  checkin?: string
  checkout?: string
  /** 档次；「不限」「无要求」或空 = 不过滤 */
  style?: string
  /** 位置关键词（地标/地址）。命不中时后端会自动放宽到全城 */
  area?: string
}

/** 选定酒店：优先用 hotelCode；兜底候选（库里没有的通用酒店）才传 name/address 等字段 */
export interface HotelSelectParams {
  /** 行程id；行程还没保存时留空（后端只校验不落库） */
  tripId?: number | string
  hotelCode?: string
  name?: string
  city?: string
  level?: string
  address?: string
  nearbyLandmark?: string
  lng?: number
  lat?: number
  price?: number
  checkin?: string
  checkout?: string
}

/** 候选酒店 — GET /hotel/search */
export function searchHotels(params: HotelSearchParams) {
  return http.get<HotelItem[]>('/hotel/search', params as Record<string, unknown>)
}

/** 选定酒店（写 trip_hotel + 回写 trip_plan.hotel）— POST /hotel/select */
export function selectHotel(data: HotelSelectParams) {
  return http.post<HotelItem>('/hotel/select', data)
}

/** 读行程已保存的住宿 — GET /hotel/trip/:tripId（没有则返回 null） */
export function getTripHotel(tripId: number | string) {
  return http.get<HotelItem | null>(`/hotel/trip/${tripId}`)
}
