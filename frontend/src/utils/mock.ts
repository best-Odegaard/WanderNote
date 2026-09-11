/** 开发阶段 Mock 数据（后端未就绪时由 api 层直接返回） */
import { HOME_ATTRACTIONS, HOME_BANNERS, HOT_CITIES } from './constant'
import type { ScenicItem } from '@/api/scenic'
import type { ActivityItem } from '@/api/activity'
import type { CommunityPost } from '@/api/community'
import type { TripPlan, TripDayPlan } from '@/api/trip'
import type { UserInfo, LoginParams, LoginResult, RegisterParams } from '@/api/user'
import type { HomeData } from '@/api/home'

/** 肇庆示例行程（与 UI 预设一致） */
export const mockZhaoqingTrip: TripPlan = {
  id: 'mock-zq-1',
  title: '肇庆1日游',
  fromCity: '广州',
  toCity: '肇庆',
  days: 1,
  budget: 4000,
  people: 2,
  tags: ['自然风光', '悠闲'],
  estimatedCost: 280,
  createdAt: '2026-05-01',
  dayPlans: [
    {
      day: 1,
      title: 'Day 1 肇庆经典',
      schedules: [
        { time: '09:00-11:30', title: '七星岩双源洞', description: '开放时间：07:30-17:30', type: 'scenic' },
        { time: '12:00-13:30', title: '岩前艺术中心', description: '开放时间：全天', type: 'food' },
        { time: '15:00-17:00', title: '星湖湿地公园', description: '开放时间：08:30-17:30', type: 'scenic' }
      ]
    }
  ]
}

export const mockTrip: TripPlan = {
  id: 'mock-hz-1',
  title: '杭州3日精品之旅',
  fromCity: '深圳',
  toCity: '杭州',
  days: 3,
  budget: 3000,
  people: 2,
  tags: ['美食', '摄影'],
  estimatedCost: 2800,
  hotel: '西湖边精品民宿',
  createdAt: '2026-05-10',
  dayPlans: [
    {
      day: 1,
      title: '西湖经典一日游',
      schedules: [
        { time: '09:00', title: '西湖', description: '漫步苏堤春晓', type: 'scenic' },
        { time: '12:00', title: '楼外楼午餐', description: '品尝杭帮菜', type: 'food' },
        { time: '15:00', title: '灵隐寺', description: '千年古刹', type: 'scenic' }
      ]
    },
    {
      day: 2,
      title: '文化深度体验',
      schedules: [
        { time: '09:00', title: '中国丝绸博物馆', type: 'scenic' },
        { time: '14:00', title: '宋城', description: '千古情演出', type: 'scenic' }
      ]
    },
    {
      day: 3,
      title: '休闲返程',
      schedules: [
        { time: '09:00', title: '西溪湿地', type: 'scenic' },
        { time: '15:00', title: '返程', type: 'transport' }
      ]
    }
  ]
}

/** 我的行程列表 */
export const mockTripList: TripPlan[] = [mockZhaoqingTrip, mockTrip]

const homeScenics: ScenicItem[] = HOME_ATTRACTIONS.map((a) => ({
  id: a.id,
  name: a.name,
  cover: a.cover,
  rating: a.rating,
  city: a.city,
  price: a.id === 2 ? 350 : a.id === 1 ? 78 : 0,
  category: '自然风光',
  openTime: '08:00-18:00',
  description: `${a.name}是${a.city}热门打卡地，适合周末出游。`,
  address: `${a.city}市`,
  reason: '推荐理由：景色优美，交通便利。'
}))

export const mockScenics: ScenicItem[] = [
  ...homeScenics,
  { id: 101, name: '西湖', cover: 'https://images.unsplash.com/photo-1439066615861-d1af74d74000?w=480&q=80&auto=format&fit=crop', rating: 4.9, city: '杭州', price: 0, category: '自然风光', openTime: '全天', description: '杭州城市名片。' },
  { id: 102, name: '灵隐寺', cover: 'https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=480&q=80&auto=format&fit=crop', rating: 4.8, city: '杭州', price: 75, category: '历史文化' },
  { id: 103, name: '宽窄巷子', cover: 'https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=480&q=80&auto=format&fit=crop', rating: 4.7, city: '成都', price: 0, category: '古镇' },
  { id: 104, name: '洪崖洞', cover: 'https://images.unsplash.com/photo-1519608487953-e999c86e7455?w=480&q=80&auto=format&fit=crop', rating: 4.8, city: '重庆', price: 0, category: '历史文化' }
]

export const mockActivities: ActivityItem[] = [
  { id: 1, title: '西湖音乐节', cover: 'https://picsum.photos/seed/music/400/300', startTime: '2026-06-01 19:00', location: '杭州西湖', city: '杭州', category: '音乐节', description: '年度音乐盛典。' },
  { id: 2, title: '成都美食节', cover: 'https://picsum.photos/seed/foodfest/400/300', startTime: '2026-05-15 10:00', location: '春熙路', city: '成都', category: '美食节' },
  { id: 3, title: '肇庆龙舟赛', cover: 'https://picsum.photos/seed/longzhou/400/300', startTime: '2026-06-10 09:00', location: '星湖', city: '肇庆', category: '文化体验' }
]

export const mockPosts: CommunityPost[] = [
  {
    id: 1,
    title: '肇庆七星岩一日游攻略',
    cover: 'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=480&q=80&auto=format&fit=crop',
    author: { id: 1, nickname: '旅行小达人', avatar: 'https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=200&q=80&auto=format&fit=crop' },
    likeCount: 2340,
    collectCount: 890,
    commentCount: 156,
    createdAt: '2026-05-20',
    tags: ['自然风光', '悠闲', '周末游'],
    location: '肇庆'
  },
  {
    id: 2,
    title: '成都火锅探店指南',
    cover: 'https://images.unsplash.com/photo-1516483638261-f4dbaf036963?w=480&q=80&auto=format&fit=crop',
    author: { id: 2, nickname: '吃货小王', avatar: 'https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=200&q=80&auto=format&fit=crop' },
    likeCount: 1890,
    collectCount: 670,
    commentCount: 98,
    createdAt: '2026-05-18',
    tags: ['美食', '探店'],
    location: '成都'
  },
  {
    id: 3,
    title: '重庆夜景 CityWalk',
    cover: 'https://images.unsplash.com/photo-1519681393784-d120267933ba?w=480&q=80&auto=format&fit=crop',
    author: { id: 3, nickname: '摄影爱好者', avatar: 'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=200&q=80&auto=format&fit=crop' },
    likeCount: 3200,
    collectCount: 1200,
    commentCount: 234,
    createdAt: '2026-05-15',
    tags: ['夜景', 'CityWalk', '摄影'],
    location: '重庆'
  }
]

export const mockUser: UserInfo = {
  id: 1,
  username: 'wandernote',
  nickname: '行笺旅人',
  avatar: 'https://picsum.photos/seed/wandernote-user/200/200',
  phone: '13800000000'
}

let tripStoreMemory = [...mockTripList]

export function getMockTripStore() {
  return tripStoreMemory
}

export function setMockTripStore(list: TripPlan[]) {
  tripStoreMemory = list
}

export function findMockTrip(id: number | string): TripPlan | undefined {
  const key = String(id)
  return tripStoreMemory.find((t) => String(t.id) === key) || (key.includes('zq') ? mockZhaoqingTrip : mockTrip)
}

export function mockLogin(_params: LoginParams): LoginResult {
  return {
    token: 'mock-token-wandernote',
    userInfo: mockUser
  }
}

export function mockRegister(_params: RegisterParams): void {
  // 注册 mock 无需操作
}

/** 安全调用 API，失败时返回 Mock（兼容旧页面写法） */
export async function withFallback<T>(apiCall: () => Promise<T>, fallback: T): Promise<T> {
  try {
    return await apiCall()
  } catch {
    console.warn('[Mock] 接口不可用，使用 fallback')
    return fallback
  }
}

export function paginate<T>(list: T[], page = 1, pageSize = 20) {
  const start = (page - 1) * pageSize
  return {
    records: list.slice(start, start + pageSize),
    total: list.length,
    page,
    pageSize
  }
}

/** 首页聚合 Mock 数据 */
export function mockHomeData(): HomeData {
  const tripStore = getMockTripStore()
  const latest = tripStore.length > 0 ? tripStore[0] : null

  return {
    banners: HOME_BANNERS.map((b) => ({
      id: b.id,
      title: b.title,
      subtitle: b.subtitle,
      emoji: b.emoji,
      imageUrl: b.imgSrc,
      linkUrl: '/pages/coming/index'
    })),
    cities: HOT_CITIES.map((c) => ({
      name: c.name,
      cover: c.cover,
      rating: c.rating
    })),
    hotAttractions: mockScenics.slice(0, 6).map((s) => ({
      id: s.id,
      name: s.name,
      cover: s.cover,
      city: s.city,
      rating: s.rating
    })),
    hotActivities: mockActivities.slice(0, 3).map((a) => ({
      id: a.id,
      title: a.title,
      cover: a.cover,
      city: a.city,
      startTime: a.startTime
    })),
    hotPosts: mockPosts.slice(0, 3).map((p) => ({
      id: p.id,
      title: p.title,
      cover: p.cover,
      authorName: p.author.nickname,
      authorAvatar: p.author.avatar,
      likeCount: p.likeCount
    })),
    latestTrip: latest
      ? {
        id: latest.id!,
        title: latest.title,
        toCity: latest.toCity,
        days: latest.days,
        createdAt: latest.createdAt || ''
      }
      : null
  }
}
