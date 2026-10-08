import http from '@/utils/request'
import type { RequestConfig } from '@/utils/request'
import { API_BASE_URL, USE_MOCK } from '@/utils/constant'
import { getToken } from '@/utils/auth'
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
  /**
   * 智能体调高德 POI 补全的景点图片 URL。
   * 后端字段名与智能体保持一致（蛇形 image_url），detail 阶段才有值。
   */
  image_url?: string
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

/**
 * 根据链接导入行程 — POST /trip/import/link
 *
 * config 透传：AI 解析外部链接（小红书/游记）经常超过默认 30s 超时，
 * 调用方需要能把 timeout 调大（见 pages/plan/import.vue 传的 120000）。
 */
export function importTripFromLink(data: ImportTripLinkParams, config?: Partial<RequestConfig>) {
  if (USE_MOCK) return mock.mockImportTripFromLink(data)
  return http.post<TripPlan>('/trip/import/link', data, {
    showLoading: true,
    loadingText: '正在识别链接...',
    ...config
  })
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
  /**
   * 智能体在 detail 阶段调高德 POI 补全的景点图片 URL（字段名与智能体一致，蛇形）。
   * 取不到时为空串。
   */
  image_url?: string
  /** 票价兜底提示（参考值），同由智能体调高德补全 */
  ticket_hint?: string
  /** 历史字段：AI 返回的景区图片列表。智能体实际从不下发该字段，仅作向后兼容 */
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
  /** 槽位快照：本轮之后收集到哪一步了（后端槽位引擎填充） */
  slot_state?: SlotState
  /**
   * 「猜你想问」：本轮之后推荐的追问，点一下就成了下一轮输入。
   * 与 slot_state 的分工：槽位收参数，这里给话题。后端纯规则生成，不额外调模型。
   */
  suggested_questions?: string[]
}

// ==================== 槽位问答（让用户点一下就能完善行程） ====================

/** 槽位的值与来源 */
export interface SlotValue {
  value: string
  /** user=用户点选/输入；inferred=系统推断；default=跳过用的默认值；imported=外部带入 */
  source: 'user' | 'inferred' | 'default' | 'imported' | string
}

/** 一个可点击选项 */
export interface SlotOption {
  label: string
  value: string
}

/** 下一个要问的问题 */
export interface SlotQuestion {
  slot: string
  text: string
  hint?: string | null
  /** 为空表示该问题需要用户自由输入 */
  options?: SlotOption[] | null
  allowSkip?: boolean
  skipLabel?: string
}

/** 能力就绪标记：由后端计算，前端只负责按标记渐显入口 */
export interface SlotReady {
  /** 可生成行程骨架（目的地 + 天数已知） */
  frame: boolean
  /** 可选酒店 */
  hotel: boolean
  /** 可查车票 */
  transport: boolean
  /** 可生成完整路线 */
  fullRoute: boolean
}

/** 槽位快照（对接后端 SlotStateVO） */
export interface SlotState {
  sessionId?: string
  slots: Record<string, SlotValue>
  /** 需求完整度 0~1 */
  completeness: number
  ready: SlotReady
  nextQuestion?: SlotQuestion | null
  askedCount?: number
}

/** 空的槽位快照：后端不可用或 mock 模式下的兜底，保证 UI 不炸 */
export function emptySlotState(): SlotState {
  return {
    slots: {},
    completeness: 0,
    ready: { frame: false, hotel: false, transport: false, fullRoute: false },
    nextQuestion: null,
    askedCount: 0
  }
}

/** 回答槽位问题（点选项 / 点跳过） — POST /travel/plan/slot */
export function submitSlotAnswer(data: {
  sessionId: string
  slot: string
  value?: string
  skipped?: boolean
}): Promise<SlotState> {
  if (USE_MOCK) return Promise.resolve(emptySlotState())
  return http.post<SlotState>('/travel/plan/slot', data, {
    timeout: 10000,
    // 带上业务 key，便于后续按业务中止；也避免被「停止生成」误伤
    key: 'slot'
  })
}

/** 查询槽位快照（刷新页面后恢复进度） — GET /travel/plan/slot/state */
export function getSlotState(sessionId: string): Promise<SlotState> {
  if (USE_MOCK) return Promise.resolve(emptySlotState())
  return http.get<SlotState>('/travel/plan/slot/state', { sessionId }, {
    timeout: 10000,
    key: 'slot-state'
  })
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
            content:
              '好嘞，我按你说的来安排 👌\n\n' +
              '- 你先点上面的选项把目的地和天数定下来\n' +
              '- 有特别想去的地方或者忌口，直接跟我说\n\n' +
              '定完我就把路线画出来 🗺️'
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
    timeout: 120000, // AI 对话较慢（多轮历史大时更慢），放宽到 2 分钟
    key: 'chat' // 「停止生成」只中止对话请求，不误伤槽位/地图等并发请求
  })
}

// ==================== 流式对话（SSE） ====================

/**
 * 当前端是否具备流式读取能力。
 *
 * 只有 H5 / App（webview 里就是浏览器）能拿到 fetch + ReadableStream；
 * 小程序端的 uni.request 不支持流式读取，调用方要回落到 {@link chatWithAi}。
 * 这也是后端同时保留整包接口的原因。
 */
export function canStreamChat(): boolean {
  // #ifdef H5 || APP-PLUS
  return typeof fetch === 'function' && typeof ReadableStream !== 'undefined'
  // #endif
  // #ifndef H5 || APP-PLUS
  return false
  // #endif
}

export interface StreamChatHandlers {
  /** 每收到一段增量文本回调一次 */
  onDelta: (text: string) => void
  /** 整轮结束：拿到与整包接口一致的响应体（session_id / chat_history / slot_state） */
  onDone: (resp: AiChatResponse) => void
}

/**
 * 流式读数据的空闲超时（毫秒）。
 *
 * 为什么需要：fetch 本身没有超时，网关/代理把连接挂住时 `reader.read()` 会一直等，
 * 而发送按钮在生成期间是「停止」图标且不可发送 —— 用户就永远卡在那里（只能切页）。
 * 这里是**空闲**超时而不是总时长超时：模型吐字慢没关系，只要一直在吐就不能掐断。
 */
const STREAM_IDLE_TIMEOUT_MS = 60_000

/**
 * 流式对话 — POST /travel/chat/stream（SSE）
 *
 * 事件协议见后端 TravelController.chatStream：
 *   delta → data 是 JSON 字符串字面量（转义过换行）
 *   done  → data 是 ChatResponseDTO 的 JSON
 *   error → data 是给用户看的一句话
 *
 * 不用 EventSource：它只能发 GET、也不能带 Authentication 头，
 * 而后端这条链路是要鉴权的 POST。所以用 fetch 拿裸流自己解析 SSE 帧。
 *
 * @param signal 外部中止信号（「停止生成」按钮用）
 */
export async function chatWithAiStream(
  data: ChatRequestParams,
  handlers: StreamChatHandlers,
  signal?: AbortSignal
): Promise<void> {
  const token = getToken()
  const res = await fetch(`${API_BASE_URL}/travel/chat/stream`, {
    method: 'POST',
    signal,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authentication: token } : {})
    },
    body: JSON.stringify(data)
  })

  if (res.status === 401) {
    // 刻意不在这里跳登录页：鉴权只交给回落后的整包链路统一处理（request.ts）。
    // 原因：流式端点比普通接口多一层不确定性（网关/代理/端点未部署都可能返回 401），
    // 如果在这里直接 redirectToLogin，用户会在"其实还能正常用"的情况下被反复弹回登录页。
    // 抛出后 tryStreamChat 会回落整包接口，那边如果确实 401 再跳，只跳一次且语义正确。
    throw new Error('未授权')
  }
  if (!res.ok || !res.body) {
    throw new Error(`流式接口不可用(${res.status})`)
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    for (;;) {
      const { value, done } = await readWithIdleTimeout(reader)
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 以空行分隔事件；Spring 会把 data 里的换行转义成多行 data:，
      // 所以这里按空行切分不会把一条消息切成两半。
      let sep = buffer.indexOf('\n\n')
      while (sep >= 0) {
        const frame = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)
        dispatchSseFrame(frame, handlers)
        sep = buffer.indexOf('\n\n')
      }
    }
  } finally {
    // 用户中途「停止生成」时主动断开，避免连接挂着
    try {
      await reader.cancel()
    } catch {
      /* 已关闭，忽略 */
    }
  }
}

/**
 * 带空闲超时的读（Web Streams 的 reader 本身没有超时选项）。
 *
 * 为什么要 Promise.race 包一层：网关挂住连接时 `reader.read()` 会一直 pending，
 * 而生成期间发送按钮是「停止」图标且不可发送 —— 用户就彻底卡住了。
 * 只做**空闲**超时：模型吐字慢没关系，只要还在吐就不能掐断。
 */
function readWithIdleTimeout(reader: {
  read: () => Promise<{ value?: Uint8Array; done: boolean }>
}): Promise<{ value?: Uint8Array; done: boolean }> {
  let timer: ReturnType<typeof setTimeout> | null = null
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(
      () => reject(new Error('流式响应超时（长时间没有数据），请重试')),
      STREAM_IDLE_TIMEOUT_MS
    )
  })
  return Promise.race([reader.read(), timeout]).finally(() => {
    if (timer) clearTimeout(timer)
  }) as Promise<{ value?: Uint8Array; done: boolean }>
}

/** 解析一条 SSE 事件帧并分发 */
function dispatchSseFrame(frame: string, handlers: StreamChatHandlers) {
  let event = 'message'
  const dataLines: string[] = []
  for (const rawLine of frame.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      // SSE 规范：data: 后若有一个空格要去掉
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }
  const payload = dataLines.join('\n')

  if (event === 'delta') {
    try {
      handlers.onDelta(JSON.parse(payload) as string)
    } catch {
      // 不是合法 JSON 就按原文处理，宁可多显示也不错丢
      handlers.onDelta(payload)
    }
  } else if (event === 'done') {
    handlers.onDone(JSON.parse(payload) as AiChatResponse)
  } else if (event === 'error') {
    throw new Error(payload || 'AI 服务暂时不可用')
  }
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
  return http.post<PlanSubmitResult>('/travel/generatePlan', data, { timeout: 30000, key: 'plan' })
}

/** 查询任务状态 — GET /travel/plan/status/:taskId */
export function getPlanStatus(taskId: string): Promise<PlanTaskStatus> {
  if (USE_MOCK) return mock.mockGetPlanStatus(taskId)
  return http.get<PlanTaskStatus>(`/travel/plan/status/${taskId}`, {}, {
    skipAuth: true,
    timeout: 15000,
    // 轮询请求若与「停止生成」共用 key，会被反复中止导致任务状态永远拿不到
    key: `plan-status-${taskId}`
  })
}

/** 取消任务 — POST /travel/plan/cancel/:taskId */
export function cancelPlanTask(taskId: string): Promise<void> {
  if (USE_MOCK) return mock.mockCancelPlan(taskId)
  return http.post<void>(`/travel/plan/cancel/${taskId}`, {}, {
    skipAuth: true,
    timeout: 10000,
    key: `plan-cancel-${taskId}`
  })
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
      // 图片优先取智能体调高德补全的 image_url；photos_json 是历史字段，智能体从不下发
      const photos = (spot.photos_json || []).filter(Boolean)
      const cover = spot.image_url || photos[0] || ''
      // 票价：AI 没给具体值时才用高德兜底提示，避免只显示"详询现场"
      const ticketText = spot.ticket || spot.ticket_hint || '详询现场'
      return {
        time: spot.visit_time_range || '09:00-11:00',
        title: spot.spot_name || '景点',
        description: `📍${spot.location || '待定'} | 🕒${spot.open_time || '全天'} | 💰${ticketText}`,
        type: 'scenic' as const,
        openTime: spot.open_time || '全天',
        ticket: ticketText,
        location: spot.location || '',
        featureTag: spot.feature_tag || '',
        image: cover,
        image_url: spot.image_url || '',
        photos: cover ? [cover, ...photos.filter((p) => p !== cover)] : photos
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
