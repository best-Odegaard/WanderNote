import http from '@/utils/request'
import { USE_MOCK } from '@/utils/constant'
import * as mock from '@/api/mock/handlers'

export interface GenerateTripParams {
  fromCity: string
  toCity: string
  days: number
  startDate?: string
  endDate?: string
  budget: number
  people: number
  tags: string[]
}

export interface ImportTripLinkParams {
  sourceUrl: string
  userId?: number
}

export interface TripScheduleItem {
  time: string
  title: string
  description?: string
  type?: 'scenic' | 'food' | 'hotel' | 'transport'
  /** AI 生成场景扩展字段 */
  openTime?: string
  ticket?: string
  location?: string
  featureTag?: string
  foodRec?: string
  rating?: number
  /** 景区封面图（取 AI photos_json 首张，缺失时前端用 emoji 占位） */
  image?: string
  /** AI 返回的全部景区图（高德图床） */
  photos?: string[]
  /** 经纬度：前端地理编码填充后用于地图绘制；后端暂未返回，缺省时由 geo.ts 解析 */
  lat?: number
  lng?: number
}

export interface TripDayPlan {
  day: number
  title: string
  schedules: TripScheduleItem[]
}

export interface TripPlan {
  id?: number | string
  /** 与该行程绑定的 AI 聊天会话；未绑定时为空 */
  chatSessionId?: string
  /** 后端 TripPlanVO 字段 */
  userId?: number
  title: string
  fromCity: string
  toCity: string
  days: number
  startDate?: string
  endDate?: string
  budget: number
  people: number
  tags: string[]
  /** 外部带入的行程上下文（如游记/景点），随行程一起保存并在对话时透传 */
  contextNote?: string
  dayPlans: TripDayPlan[]
  estimatedCost?: number
  hotel?: string
  cover?: string
  sourceUrl?: string
  sourceType?: number
  visibility?: number
  status?: number
  /** 后端 VO 新增字段 */
  likeCount?: number
  shareCount?: number
  createTime?: string
  updateTime?: string
  createdAt?: string
}

/** 根据链接导入行程 — POST /trip/import/link */
export function importTripFromLink(data: ImportTripLinkParams) {
  if (USE_MOCK) return mock.mockImportTripFromLink(data)
  return http.post<TripPlan>('/trip/import/link', data, { showLoading: true, loadingText: '正在识别链接...' })
}

/** 保存行程 — POST /trip/save */
export function saveTrip(data: TripPlan) {
  if (USE_MOCK) return mock.mockSaveTrip(data)
  return http.post<TripPlan>('/trip/save', data, { showLoading: true })
}

/** 我的行程列表 — GET /trip/list */
export function getMyTrips() {
  if (USE_MOCK) return mock.mockGetMyTrips()
  return http.get<TripPlan[]>('/trip/list')
}

/** 行程详情 — GET /trip/:id */
export function getTripDetail(id: number | string) {
  if (USE_MOCK) return mock.mockGetTripDetail(id)
  return http.get<TripPlan>(`/trip/${id}`)
}

/** 更新行程 — PUT /trip/:id */
export function updateTrip(id: number | string, data: Partial<TripPlan>) {
  if (USE_MOCK) return mock.mockUpdateTrip(id, data)
  return http.put<TripPlan>(`/trip/${id}`, data, { showLoading: true })
}

/** 删除行程 — DELETE /trip/:id */
export function deleteTrip(id: number | string) {
  if (USE_MOCK) return mock.mockDeleteTrip(id)
  return http.delete<void>(`/trip/${id}`, undefined, { showLoading: true })
}

/** AI Agent 请求参数（对接后端 AgentRequestDTO） */
export interface AgentRequestParams {
  /** 会话id（首次为空，后端创建后返回；后续多轮需带上） */
  session_id?: string
  /** 本轮用户输入/追加指令（为空表示首轮"生成最终行程"） */
  user_input?: string
  departure_city: string
  destination_city: string
  start_day: string
  end_date: string
  days: number
  hobby: string[]
  people_num: string
  budget: string
}

/** 聊天历史消息（对接后端 ChatMessageVO，回传给智能体做多轮记忆） */
export interface ChatMessageVO {
  role: 'user' | 'assistant'
  content: string
  isPlan?: number
  planDataJson?: string
  createTime?: string
}

/** AI Agent 返回的景点信息（对接后端 AttractionDTO） */
export interface AiAttraction {
  visit_time_range: string
  spot_name: string
  open_time: string
  ticket: string
  location: string
  feature_tag: string
  /** AI 返回的景区图片列表（高德图床 URL） */
  photos_json?: string[]
}

/** AI Agent 返回的每日行程（对接后端 DayScheduleDTO） */
export interface AiDaySchedule {
  date: string
  schedule: AiAttraction[]
  total_spot: number
  total_distance: number
}

/** AI Agent 返回的行程计划（对接后端 TripPlanDTO） */
export interface AiTripPlan {
  title: string
  day_list: AiDaySchedule[]
  total_walk: string
}

/** AI Agent 完整响应（对接后端 PlanResponseDTO） */
export interface PlanResponse {
  plan_data: AiTripPlan
}

/** 行程框架（阶段一产物，仅含每日景点名单，用于骨架预览） */
export interface TripPlanFrame {
  title: string
  total_walk: string
  day_list: Array<{ date: string; spot_names: string[] }>
}

/** 提交行程生成任务的返回（对接后端 PlanSubmitVO） */
export interface PlanSubmitResult {
  taskId?: string
  /** true 表示命中缓存，plan 直接可用，无需轮询 */
  fromCache: boolean
  plan?: PlanResponse
}

/** 行程生成任务状态（对接后端 PlanTaskVO，前端轮询） */
export interface PlanTaskStatus {
  taskId: string
  status: 'PROCESSING' | 'DONE' | 'ERROR' | 'CANCELED'
  stage: 'retrieving' | 'generating_frame' | 'generating_detail' | 'done' | 'canceled' | string
  message: string
  elapsedSec: number
  frame?: TripPlanFrame
  plan?: PlanResponse
  errorMsg?: string
}

/** 生成进度回调数据 */
export interface GenerateProgress {
  stage: string
  message: string
  elapsedSec: number
  frame?: TripPlanFrame
}

/** 生成完成结果 */
export interface GenerateResult {
  plan: PlanResponse
  fromCache: boolean
  elapsedSec: number
}

/** AI 多轮会话响应（对接后端 ChatResponseDTO） */
export interface AiChatResponse {
  /** 后端字段 session_id */
  session_id: string
  /** AI 回复文本 */
  reply: string
  /** 完整对话历史（含本轮 user + assistant） */
  chat_history: ChatMessageVO[]
}

// ==================== 新增 AI Agent API ====================

/** AI 长对话请求体（对接后端 ChatRequestDTO） */
export interface ChatRequestParams {
  /** 会话id（首次为空，后端创建后返回；后续多轮需带上） */
  session_id?: string
  /** 本轮用户输入 */
  user_input?: string
  /** 基础行程信息 */
  base_info: BaseInfoParams
  /** 对话历史（多轮记忆，后端原样回传给智能体） */
  chat_history?: ChatMessageVO[]
}

/** 基础行程信息（对接后端 BaseInfoDTO） */
export interface BaseInfoParams {
  departure_city: string
  destination_city: string
  start_day: string
  end_date: string
  days: number
  hobby: string[]
  people_num: string
  budget: string
  /** 外部带入的行程上下文（如游记/景点），透传给智能体作为首轮提示词 */
  context_note?: string
}

// ==================== AI 长对话 / 生成计划 API（对接后端 /travel/* ） ====================

// Mock 模式下按 session 保存会话，模拟后端 chat_history 表。
const mockChatHistories = new Map<string, ChatMessageVO[]>()

/**
 * AI 长对话聊天 — POST /travel/chat
 * 返回 AiChatResponse（session_id + reply + chat_history）。
 * 首次可不传 session_id（后端创建并返回）；后续多轮需带上 session_id 与 user_input。
 */
export function chatWithAi(data: ChatRequestParams): Promise<AiChatResponse> {
  if (USE_MOCK) {
    return new Promise<AiChatResponse>((resolve) => {
      setTimeout(() => {
        const sessionId = data.session_id || 'mock_session_' + Date.now()
        const history = [...(mockChatHistories.get(sessionId) || data.chat_history || [])]
        if (data.user_input) {
          history.push({ role: 'user', content: data.user_input })
          history.push({
            role: 'assistant',
            content: '已收到你的需求，我正在为你规划。你可以继续补充偏好（如美食、自然风光、历史文化），或直接点击下方"生成行程计划"。'
          })
        }
        mockChatHistories.set(sessionId, history)
        resolve({
          session_id: sessionId,
          reply: history[history.length - 1]?.content || '（AI 暂无回复）',
          chat_history: history
        })
      }, 1200)
    })
  }
  // 对话链路已收回登录态（后端要按 user_id 沉淀画像），不能再用 skipAuth：
  // 不加 token 会被后端 401，用户只会看到"请先登录"
  return http.post<AiChatResponse>('/travel/chat', data, {
    timeout: 120000 // AI 对话较慢（多轮历史大时更慢），放宽到 2 分钟
  })
}

/**
 * 长对话后生成完整行程计划（异步任务版）：
 * 提交后立即返回 taskId，前端轮询 /travel/plan/status/{taskId} 获取阶段进度；
 * 同一行程命中 Redis 缓存时直接返回完整行程（fromCache=true），秒开。
 */
export function generateTravelPlan(
  data: ChatRequestParams,
  options?: {
    onProgress?: (p: GenerateProgress) => void
    shouldCancel?: () => boolean
  }
): Promise<GenerateResult> {
  if (USE_MOCK) {
    return mockGenerateTravelPlan(data, options)
  }
  return realGenerateTravelPlan(data, options)
}

/** 提交生成任务 — POST /travel/generatePlan（需登录：生成结果要关联用户画像） */
export function submitGeneratePlan(data: ChatRequestParams): Promise<PlanSubmitResult> {
  if (USE_MOCK) return mock.mockSubmitPlan(data)
  return http.post<PlanSubmitResult>('/travel/generatePlan', data, { timeout: 30000 })
}

/** 查询任务状态 — GET /travel/plan/status/:taskId */
export function getPlanStatus(taskId: string): Promise<PlanTaskStatus> {
  if (USE_MOCK) return mock.mockGetPlanStatus(taskId)
  return http.get<PlanTaskStatus>(`/travel/plan/status/${taskId}`, {}, { skipAuth: true, timeout: 15000 })
}

/** 取消任务 — POST /travel/plan/cancel/:taskId */
export function cancelPlanTask(taskId: string): Promise<void> {
  if (USE_MOCK) return mock.mockCancelPlan(taskId)
  return http.post<void>(`/travel/plan/cancel/${taskId}`, {}, { skipAuth: true, timeout: 10000 })
}

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))

/** 真实链路：提交 → 轮询（2.5s/次，超时 4 分钟）→ 返回最终行程 */
async function realGenerateTravelPlan(
  data: ChatRequestParams,
  options?: { onProgress?: (p: GenerateProgress) => void; shouldCancel?: () => boolean }
): Promise<GenerateResult> {
  const res = await submitGeneratePlan(data)
  if (res.fromCache && res.plan) {
    return { plan: res.plan, fromCache: true, elapsedSec: 0 }
  }
  const taskId = res.taskId || ''
  const deadline = Date.now() + 4 * 60 * 1000 // 兜底 4 分钟
  // eslint-disable-next-line no-constant-condition
  while (true) {
    if (options?.shouldCancel?.()) {
      await cancelPlanTask(taskId).catch(() => {})
      throw new Error('CANCELED')
    }
    if (Date.now() > deadline) {
      throw new Error('生成超时，请稍后重试')
    }
    await sleep(2500)
    const st = await getPlanStatus(taskId)
    const progress: GenerateProgress = {
      stage: st.stage,
      message: st.message,
      elapsedSec: st.elapsedSec,
      frame: st.frame
    }
    options?.onProgress?.(progress)
    if (st.status === 'DONE' && st.plan) {
      return { plan: st.plan, fromCache: false, elapsedSec: st.elapsedSec }
    }
    if (st.status === 'ERROR') {
      throw new Error(st.errorMsg || st.message || '生成失败，请稍后重试')
    }
    if (st.status === 'CANCELED') {
      throw new Error('已取消生成')
    }
  }
}

/** Mock 链路：模拟两阶段进度（框架→详情），快速演示 */
async function mockGenerateTravelPlan(
  data: ChatRequestParams,
  options?: { onProgress?: (p: GenerateProgress) => void; shouldCancel?: () => boolean }
): Promise<GenerateResult> {
  const res = await mock.mockSubmitPlan(data)
  if (res.fromCache && res.plan) {
    return { plan: res.plan, fromCache: true, elapsedSec: 0 }
  }
  const taskId = res.taskId || 'mock_task'
  const stages: GenerateProgress[] = [
    { stage: 'retrieving', message: '正在检索景点与攻略…', elapsedSec: 1 },
    { stage: 'generating_frame', message: 'AI 正在生成行程框架…', elapsedSec: 3 },
    { stage: 'generating_frame', message: '行程框架已生成，正在细化每日行程…', elapsedSec: 6 }
  ]
  for (const s of stages) {
    await sleep(700)
    if (options?.shouldCancel?.()) {
      await mock.mockCancelPlan(taskId)
      throw new Error('已取消生成')
    }
    if (s.stage === 'generating_frame') {
      s.frame = await mock.mockGetPlanStatus(taskId).then((st) => st.frame)
    }
    options?.onProgress?.(s)
  }
  await sleep(800)
  // 收尾轮询直到 DONE（mock 状态机第 4 次调用才返回 plan）
  let st = await mock.mockGetPlanStatus(taskId)
  let guard = 0
  while (st.status !== 'DONE' && guard < 5) {
    await sleep(300)
    st = await mock.mockGetPlanStatus(taskId)
    guard++
  }
  return { plan: st.plan!, fromCache: false, elapsedSec: st.elapsedSec }
}

/**
 * 查询长对话会话历史（/travel/chat 链路，chat_history 表）— GET /travel/history?sessionId=
 * 需登录：后端会校验会话归属，不是本人的会话直接返回空
 */
export function getTravelHistory(sessionId: string): Promise<ChatMessageVO[]> {
  if (USE_MOCK) {
    return Promise.resolve([...(mockChatHistories.get(sessionId) || [])])
  }
  return http.get<ChatMessageVO[]>('/travel/history', { sessionId })
}

/**
 * 将 AI Agent 返回的 PlanResponse 映射为前端 TripPlan 格式
 */
export function mapPlanResponseToTripPlan(
  response: PlanResponse,
  params: { fromCity: string; toCity: string; tags: string[] }
): TripPlan {
  const plan = response.plan_data
  if (!plan) {
    return {
      title: 'AI 生成行程',
      fromCity: params.fromCity || '',
      toCity: params.toCity || '',
      days: 0,
      budget: 0,
      people: 1,
      tags: params.tags || [],
      dayPlans: []
    }
  }

  const dayPlans: TripDayPlan[] = (plan.day_list || []).map((day, di) => ({
    day: di + 1,
    title: day.date || `第${di + 1}天`,
    schedules: (day.schedule || []).map((spot) => {
      const timeParts = (spot.visit_time_range || '09:00-11:00').split('-')
      const photos = (spot.photos_json || []).filter(Boolean)
      return {
        time: spot.visit_time_range || '09:00-11:00',
        title: spot.spot_name || '景点',
        description: `📍${spot.location || '待定'} | 🕒${spot.open_time || '全天'} | 💰${spot.ticket || '详询现场'}`,
        type: 'scenic' as const,
        openTime: spot.open_time || '全天',
        ticket: spot.ticket || '详询现场',
        location: spot.location || '',
        featureTag: spot.feature_tag || '',
        image: photos[0] || '',
        photos
      }
    })
  }))

  return {
    title: plan.title || `${params.toCity}之旅`,
    fromCity: params.fromCity || '',
    toCity: params.toCity || '',
    days: dayPlans.length || 1,
    budget: 0,
    people: 1,
    tags: params.tags || [],
    dayPlans,
    estimatedCost: 0
  }
}
