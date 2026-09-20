import http from '@/utils/request'
import { USE_MOCK } from '@/utils/constant'
import * as mock from '@/api/mock/handlers'
import type { TripPlan } from '@/api/trip'

// ── 首页聚合数据模型（对齐后端 HomeVO）──

export interface BannerItem {
  id: number
  title: string
  subtitle: string
  /**
   * 可选：早期用于在轮播右下角画一个装饰 emoji。
   * 现已移除该渲染（精选行程位会写死 🧭，看起来像水印），字段保留可选以兼容旧数据。
   */
  emoji?: string
  imageUrl?: string
  linkUrl?: string
}

export interface CityItem {
  name: string
  cover: string
  rating: number
}

export interface HomeScenicItem {
  id: number
  name: string
  cover: string
  city: string
  rating: number
}

export interface HomeActivityItem {
  id: number
  title: string
  cover: string
  city: string
  startTime: string
}

export interface HomePostItem {
  id: number
  title: string
  cover: string
  authorName: string
  authorAvatar: string
  likeCount: number
}

export interface TripSummary {
  id: number | string
  title: string
  toCity: string
  days: number
  createdAt: string
}

export interface HomeData {
  banners: BannerItem[]
  cities: CityItem[]
  hotAttractions: HomeScenicItem[]
  hotActivities: HomeActivityItem[]
  hotPosts: HomePostItem[]
  latestTrip: TripSummary | null
}

/** 获取首页聚合数据 — GET /home/index */
export function getHomeIndex(): Promise<HomeData> {
  if (USE_MOCK) return mock.mockGetHomeIndex()
  return http.get<HomeData>('/home/index')
}
