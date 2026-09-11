/**
 * Mock API 实现（与 src/api/*.ts 接口路径一一对应，后端就绪后关闭 VITE_USE_MOCK）
 */
import type { ScenicQuery, ScenicItem, PageResult as ScenicPageResult } from '@/api/scenic'
import type { ActivityQuery, ActivityItem, PageResult as ActivityPageResult } from '@/api/activity'
import type { CommunityQuery, CommunityPost, PublishPostParams, PageResult as CommunityPageResult } from '@/api/community'
import type { ImportTripLinkParams, TripPlan, PlanSubmitResult, PlanTaskStatus, TripPlanFrame, PlanResponse } from '@/api/trip'
import type { LoginParams, LoginResult, RegisterParams, UserInfo } from '@/api/user'
import type { HomeData } from '@/api/home'
import {
  mockScenics,
  mockActivities,
  mockPosts,
  mockUser,
  mockLogin,
  mockRegister,
  mockTripList,
  findMockTrip,
  getMockTripStore,
  setMockTripStore,
  paginate,
  mockHomeData
} from '@/utils/mock'

// —— 行程 ——
export function mockGetMyTrips(): Promise<TripPlan[]> {
  return Promise.resolve([...getMockTripStore()])
}

export function mockGetTripDetail(id: number | string): Promise<TripPlan> {
  const trip = findMockTrip(id)
  if (!trip) return Promise.reject(new Error('行程不存在'))
  return Promise.resolve({ ...trip })
}

export function mockImportTripFromLink(params: ImportTripLinkParams): Promise<TripPlan> {
  const city = extractCityFromUrl(params.sourceUrl)
  const trip: TripPlan = {
    ...mockTripList[0],
    id: `mock-import-${Date.now()}`,
    title: city ? `${city}3日游｜链接导入行程` : '导入的行程（待解析目的地）',
    toCity: city || '',
    days: 3,
    sourceUrl: params.sourceUrl,
    createdAt: new Date().toISOString().slice(0, 10)
  } as TripPlan
  const list = getMockTripStore()
  list.unshift(trip)
  setMockTripStore(list)
  return Promise.resolve(trip)
}

/** 从链接文本中简单提取城市名（与后端提取规则保持一致的简化版），提取不到返回空串 */
function extractCityFromUrl(url: string): string {
  if (!url) return ''
  const withSuffix = url.match(/([\u4e00-\u9fa5]{2,5}?)市/)
  if (withSuffix) return withSuffix[1]
  const beforeKeyword = url.match(/([\u4e00-\u9fa5]{2,5}?)(?:旅游|游记|攻略|打卡|美食|[0-9一二三四五六日]+日游|玩)/)
  if (beforeKeyword) return beforeKeyword[1].replace(/^[去游]/, '')
  return ''
}

export function mockSaveTrip(data: TripPlan): Promise<TripPlan> {
  const saved = { ...data, id: data.id || `mock-${Date.now()}` }
  const list = getMockTripStore()
  const idx = list.findIndex((t) => String(t.id) === String(saved.id))
  if (idx >= 0) list[idx] = saved
  else list.unshift(saved)
  setMockTripStore(list)
  return Promise.resolve(saved)
}

export function mockUpdateTrip(id: number | string, data: Partial<TripPlan>): Promise<TripPlan> {
  const list = getMockTripStore()
  const idx = list.findIndex((t) => String(t.id) === String(id))
  if (idx < 0) return Promise.reject(new Error('行程不存在'))
  list[idx] = { ...list[idx], ...data }
  setMockTripStore(list)
  return Promise.resolve(list[idx])
}

export function mockDeleteTrip(id: number | string): Promise<void> {
  setMockTripStore(getMockTripStore().filter((t) => String(t.id) !== String(id)))
  return Promise.resolve()
}

export function mockCopyTrip(id: number | string): Promise<TripPlan> {
  const src = findMockTrip(id)
  if (!src) return Promise.reject(new Error('行程不存在'))
  const copy = { ...src, id: `mock-copy-${Date.now()}`, title: `${src.title}（副本）` }
  const list = getMockTripStore()
  list.unshift(copy)
  setMockTripStore(list)
  return Promise.resolve(copy)
}

// —— AI 行程生成任务（异步两阶段：框架 → 详情） ——

/** 按目的地动态生成 Mock 计划数据（不写死任何城市） */
function buildMockPlanResponse(destination: string, days: number): PlanResponse {
  const safeDays = Math.max(1, Math.min(days || 2, 7))
  const city = destination || '目的地'
  const dayList = Array.from({ length: safeDays }, (_, i) => {
    const spots = [
      { visit_time_range: '09:00-12:00', spot_name: `${city}城市地标`, open_time: '08:00-18:00', ticket: '现场咨询', location: `${city}市中心`, feature_tag: '拍照出片' },
      { visit_time_range: '13:30-16:00', spot_name: `${city}老街漫游`, open_time: '全天', ticket: '免费', location: `${city}老城区`, feature_tag: '历史文化' },
      { visit_time_range: '18:00-20:00', spot_name: `${city}美食街`, open_time: '全天', ticket: '免费', location: `${city}美食街区`, feature_tag: '美食天堂' }
    ]
    if (i > 0) spots.splice(2, 1) // 后几天减少一个点，模拟节奏变化
    return {
      date: `Day${i + 1}`,
      schedule: spots,
      total_spot: spots.length,
      total_distance: 8 + i * 2
    }
  })
  return {
    plan_data: {
      title: `${city}${safeDays}日经典游`,
      day_list: dayList,
      total_walk: `约${safeDays * 3}小时`
    }
  }
}

/** 由完整行程构建 Mock 行程框架（阶段一骨架预览） */
function buildMockFrame(plan: PlanResponse): TripPlanFrame {
  return {
    title: plan.plan_data.title,
    total_walk: plan.plan_data.total_walk,
    day_list: plan.plan_data.day_list.map((d) => ({
      date: d.date,
      spot_names: d.schedule.map((s) => s.spot_name)
    }))
  }
}

/** 每个 mock 任务的状态机推进次数 */
const mockTaskTicks = new Map<string, number>()
/** 已完成生成的行程 key -> plan（模拟 Redis 缓存命中） */
const mockPlanCache = new Map<string, PlanResponse>()
/** taskId -> 生成结果（submit 时按目的地动态构建） */
const mockTaskData = new Map<string, { plan: PlanResponse; frame: TripPlanFrame; key: string }>()

/** 由请求参数生成稳定 key（模拟 城市+天数 缓存维度） */
function mockPlanKey(data: unknown): string {
  const d = data as { base_info?: { destination_city?: string; days?: number } }
  return `mock:${d?.base_info?.destination_city || 'x'}:${d?.base_info?.days || 0}`
}

export function mockSubmitPlan(data: unknown): Promise<PlanSubmitResult> {
  const key = mockPlanKey(data)
  const cached = mockPlanCache.get(key)
  if (cached) {
    return Promise.resolve({ fromCache: true, plan: cached })
  }
  const base = (data as { base_info?: { destination_city?: string; days?: number } }).base_info || {}
  const plan = buildMockPlanResponse(base.destination_city || '', base.days || 2)
  const frame = buildMockFrame(plan)
  const taskId = `mock_task_${key}_${Date.now()}`
  mockTaskTicks.set(taskId, 0)
  mockTaskData.set(taskId, { plan, frame, key })
  return Promise.resolve({ taskId, fromCache: false })
}

export function mockGetPlanStatus(taskId: string): Promise<PlanTaskStatus> {
  const tick = (mockTaskTicks.get(taskId) || 0) + 1
  mockTaskTicks.set(taskId, tick)
  const elapsedSec = tick * 3
  const data = mockTaskData.get(taskId)
  const plan = data?.plan || buildMockPlanResponse('目的地', 2)
  const frame = data?.frame || buildMockFrame(plan)
  if (tick === 1) {
    return Promise.resolve({ taskId, status: 'PROCESSING', stage: 'retrieving', message: '正在检索景点与攻略…', elapsedSec, errorMsg: '' })
  }
  if (tick === 2) {
    return Promise.resolve({ taskId, status: 'PROCESSING', stage: 'generating_frame', message: 'AI 正在生成行程框架…', elapsedSec, frame, errorMsg: '' })
  }
  if (tick === 3) {
    return Promise.resolve({ taskId, status: 'PROCESSING', stage: 'generating_detail', message: 'AI 正在细化每日行程…', elapsedSec, frame, errorMsg: '' })
  }
  // tick >= 4 完成，并写入"缓存"（同一参数再次提交直接命中）
  if (data?.key) mockPlanCache.set(data.key, plan)
  return Promise.resolve({ taskId, status: 'DONE', stage: 'done', message: '生成完成', elapsedSec, frame, plan, errorMsg: '' })
}

export function mockCancelPlan(taskId: string): Promise<void> {
  mockTaskTicks.set(taskId, -1)
  return Promise.resolve()
}

// —— 景点 ——
function filterScenics(params: ScenicQuery): ScenicItem[] {
  let list = [...mockScenics]
  if (params.keyword) {
    const kw = params.keyword.toLowerCase()
    list = list.filter((s) => s.name.includes(kw) || s.city.includes(kw))
  }
  if (params.city) list = list.filter((s) => s.city === params.city)
  if (params.category && params.category !== '全部') {
    list = list.filter((s) => s.category === params.category)
  }
  return list
}

export function mockGetScenicList(params: ScenicQuery): Promise<ScenicPageResult<ScenicItem>> {
  const list = filterScenics(params)
  return Promise.resolve(paginate(list, params.page, params.pageSize))
}

export function mockGetScenicDetail(id: number): Promise<ScenicItem> {
  const item = mockScenics.find((s) => s.id === id) || mockScenics[0]
  return Promise.resolve({ ...item, images: [item.cover, item.cover] })
}

export function mockGetHotScenics(limit = 10): Promise<ScenicItem[]> {
  return Promise.resolve(mockScenics.slice(0, limit))
}

export function mockSearchScenic(keyword: string): Promise<ScenicItem[]> {
  return Promise.resolve(filterScenics({ keyword }))
}

export function mockCollectScenic(_id: number): Promise<void> {
  return Promise.resolve()
}

export function mockUncollectScenic(_id: number): Promise<void> {
  return Promise.resolve()
}

// —— 活动 ——
function filterActivities(params: ActivityQuery): ActivityItem[] {
  let list = [...mockActivities]
  if (params.city) list = list.filter((a) => a.city === params.city)
  if (params.category && params.category !== '全部') {
    list = list.filter((a) => a.category === params.category)
  }
  return list
}

export function mockGetActivityList(params: ActivityQuery): Promise<ActivityPageResult<ActivityItem>> {
  const list = filterActivities(params)
  return Promise.resolve({
    records: paginate(list, params.page, params.pageSize).records,
    total: list.length
  })
}

export function mockGetActivityDetail(id: number): Promise<ActivityItem> {
  const item = mockActivities.find((a) => a.id === id) || mockActivities[0]
  return Promise.resolve({ ...item })
}

export function mockGetHotActivities(limit = 10): Promise<ActivityItem[]> {
  return Promise.resolve(mockActivities.slice(0, limit))
}

export function mockCollectActivity(_id: number): Promise<void> {
  return Promise.resolve()
}

export function mockUncollectActivity(_id: number): Promise<void> {
  return Promise.resolve()
}

export function mockEnrollActivity(_id: number): Promise<void> {
  return Promise.resolve()
}

// —— 社区 ——
export function mockGetCommunityList(params: CommunityQuery): Promise<CommunityPageResult<CommunityPost>> {
  let list = [...mockPosts]
  if (params.keyword) {
    const kw = params.keyword
    list = list.filter((p) => p.title.includes(kw))
  }
  const pageNum = params.pageNum || 1
  const pageSize = params.pageSize || 10
  return Promise.resolve({
    records: paginate(list, pageNum, pageSize).records,
    total: list.length
  })
}

export function mockGetCommunityDetail(id: number): Promise<CommunityPost> {
  const post = mockPosts.find((p) => p.id === id) || mockPosts[0]
  return Promise.resolve({ ...post, content: post.title + ' 的详细内容（Mock）。' })
}

export function mockPublishPost(data: PublishPostParams): Promise<CommunityPost> {
  const images = data.imageUrls && data.imageUrls.length > 0
    ? data.imageUrls
    : [`https://picsum.photos/seed/newpost-${Date.now()}/400/500`]
  const post: CommunityPost = {
    id: Date.now(),
    title: data.title,
    cover: images[0],
    content: data.content,
    images,
    tags: data.tags || [],
    location: data.location || '',
    author: { id: 1, nickname: mockUser.nickname, avatar: mockUser.avatar },
    likeCount: 0,
    collectCount: 0,
    commentCount: 0,
    createdAt: new Date().toISOString().slice(0, 10)
  }
  mockPosts.unshift(post)
  return Promise.resolve(post)
}

/** 点赞/取消点赞 toggle（对齐后端 PUT /journal/like/{journalId}） */
export function mockToggleJournalLike(_journalId: number): Promise<void> {
  return Promise.resolve()
}

export function mockLikePost(_id: number): Promise<void> {
  return Promise.resolve()
}

export function mockUnlikePost(_id: number): Promise<void> {
  return Promise.resolve()
}

export function mockCollectPost(_id: number): Promise<void> {
  return Promise.resolve()
}

export function mockUncollectPost(_id: number): Promise<void> {
  return Promise.resolve()
}

export function mockFollowAuthor(_id: number): Promise<void> {
  return Promise.resolve()
}

export function mockGetHotPosts(limit = 10): Promise<CommunityPost[]> {
  return Promise.resolve(mockPosts.slice(0, limit))
}

export function mockCommentPost(_postId: number, _content: string): Promise<void> {
  return Promise.resolve()
}

/** 删除游记（Mock：从 mockPosts 中移除） */
export function mockDeletePost(journalId: number): Promise<void> {
  const idx = mockPosts.findIndex((p) => p.id === journalId)
  if (idx >= 0) mockPosts.splice(idx, 1)
  return Promise.resolve()
}

/** 我的游记列表（Mock：取 mockPosts 前几条） */
export function mockGetMyJournals(): Promise<CommunityPost[]> {
  return Promise.resolve(mockPosts.slice(0, 3))
}

/** 我的收藏游记列表（Mock） */
export function mockGetMyCollects(): Promise<CommunityPost[]> {
  return Promise.resolve(mockPosts.slice(0, 2))
}

// —— 用户 ——
export function mockUserLogin(params: LoginParams): Promise<LoginResult> {
  return Promise.resolve(mockLogin(params))
}

export function mockUserRegister(_params: RegisterParams): Promise<void> {
  return Promise.resolve(mockRegister(_params))
}

export function mockGetUserInfo(): Promise<UserInfo> {
  return Promise.resolve({ ...mockUser })
}

export function mockUpdateUserInfo(data: Partial<UserInfo>): Promise<UserInfo> {
  return Promise.resolve({ ...mockUser, ...data })
}

export function mockSendSmsCode(_phone: string): Promise<void> {
  return Promise.resolve()
}

// —— 首页 ——
export function mockGetHomeIndex(): Promise<HomeData> {
  return Promise.resolve(mockHomeData())
}

// —— 意见反馈 ——
export function mockSubmitFeedback(_data: Record<string, unknown>): Promise<void> {
  return Promise.resolve()
}
