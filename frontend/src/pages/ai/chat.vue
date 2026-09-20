<template>
  <view class="page">
    <view class="nav" :style="{ paddingTop: statusBarHeight + 'px', height: navHeight + 'px' }">
      <view class="nav-inner">
        <view class="back" @tap="goBack">
          <AppIcon name="chevron-left" :size="34" color="var(--text-body)" />
        </view>
        <text class="nav-title">AI行程助手</text>
        <text class="draft-btn" @tap="onDraft">保存草稿</text>
      </view>
    </view>

    <scroll-view
      scroll-y
      class="chat-scroll"
      :style="{ paddingTop: navHeight + 'px' }"
      :scroll-into-view="scrollIntoView"
      scroll-with-animation
    >
      <!-- 聊天消息列表 -->
      <view
        v-for="(msg, i) in messages"
        :key="i"
        :id="'msg-' + i"
        class="msg-row"
        :class="msg.role"
      >
        <view v-if="msg.role === 'assistant'" class="avatar ai-avatar">
          <AppIcon name="robot" :size="34" color="#ffffff" />
        </view>
        <view class="bubble" :class="msg.role">
          <text v-if="msg.role === 'assistant'" class="role-tag">AI</text>
          <text v-else class="role-tag user-tag">我</text>
          <!-- AI 回复按 Markdown 渲染（加粗/列表/标题/链接等），用户消息保持纯文本 -->
          <view v-if="msg.role === 'assistant'" class="msg-md" v-html="renderMarkdown(msg.content)" />
          <text v-else class="msg-text">{{ msg.content }}</text>

          <!-- AI 生成的行程预览卡片 -->
          <view v-if="msg.planPreview" class="plan-preview-card">
            <text class="plan-title">{{ msg.planPreview.title }}</text>
            <view class="plan-stats">
              <text class="stat">{{ msg.planPreview.days }}天行程</text>
              <text class="stat">{{ msg.planPreview.totalSpots }}个景点</text>
              <text v-if="msg.planPreview.totalWalk" class="stat">
                <AppIcon name="walk" :size="24" color="var(--text-body)" />
                {{ msg.planPreview.totalWalk }}
              </text>
            </view>
            <view class="day-list">
              <view v-for="day in msg.planPreview.daysPreview" :key="day.label" class="day-row">
                <text class="day-label">{{ day.label }}</text>
                <text class="day-spots">{{ day.spots }}</text>
              </view>
            </view>
            <!-- 行程生成后：查看详情（含地图路线：按天分色 / 总览） -->
            <button class="btn-black detail-btn" @tap="goPlanDetail(msg.tripId || '')">
              查看详情 →
            </button>
          </view>
        </view>
      </view>

      <!-- loading 状态（显示在用户最后一条消息下方，即 AI 回复的位置） -->
      <view v-if="loadingAi" class="msg-row assistant">
        <view class="avatar ai-avatar">
          <AppIcon name="robot" :size="34" color="#ffffff" />
        </view>
        <view class="bubble assistant thinking-bubble">
          <text class="role-tag">AI</text>
          <view class="thinking-dots">
            <view class="dot" />
            <view class="dot" />
            <view class="dot" />
          </view>
          <text class="thinking-text">{{ loadingText }}</text>
        </view>
      </view>

      <!-- 错误提示（位于消息流下方） -->
      <view v-if="aiError" class="error-card">
        <AppIcon name="alert" :size="32" color="var(--danger)" class="error-icon" />
        <text class="error-text">{{ aiError }}</text>
        <view class="retry-btn" @tap="retryAiCall">
          <text>重新生成</text>
        </view>
      </view>

      <view :id="'msg-' + messages.length" />
      <view style="height: 300rpx" />
    </scroll-view>

      <!-- 生成行程计划进度遮罩（异步任务：检索 → 框架 → 详情，可取消） -->
    <view v-if="finalizing" class="gen-overlay">
      <view class="gen-card">
        <view class="gen-spinner" />
        <text class="gen-title">正在生成行程计划</text>
        <text class="gen-stage">{{ genMessage }}</text>
        <text class="gen-elapsed">已等待 {{ genElapsed }} 秒</text>

        <!-- 阶段一产物：行程骨架预览（秒出） -->
        <view v-if="genFrame" class="gen-frame">
          <view class="gen-frame-title">
            <AppIcon name="map-pin" :size="26" color="var(--text-body)" />
            <text>{{ genFrame.title }}</text>
          </view>
          <view v-for="(d, i) in genFrame.day_list" :key="i" class="gen-frame-day">
            <text class="gen-frame-label">{{ d.date }} · {{ (d.spot_names || []).length }}个景点</text>
            <text class="gen-frame-spots">{{ (d.spot_names || []).join(' → ') || '待安排' }}</text>
          </view>
          <text class="gen-frame-tip">已确定行程框架，正在补充开放时间、门票等详情…</text>
        </view>

        <!-- 边生成边画：frame 出来后在地图上逐点绘制路线 -->
        <view v-if="genFrame" class="gen-map-wrap">
          <TripMap
            ref="genMapRef"
            :city="currentTrip?.toCity || ''"
            height="360rpx"
            empty-text="正在定位景点…"
            @spot-tap="onGenMapSpotTap"
          />
          <view class="gen-map-day">
            <text class="gen-map-day-label">第{{ genMapDayIndex + 1 }}天路线 · 已定位 {{ genDrawnCount }} 个景点</text>
            <view v-if="(genFrame.day_list || []).length > 1" class="gen-map-day-switch">
              <text class="gen-map-arrow" @tap="switchGenMapDay(-1)">‹</text>
              <text class="gen-map-arrow" @tap="switchGenMapDay(1)">›</text>
            </view>
          </view>
        </view>

        <view class="gen-cancel" @tap="cancelGeneration">取消生成</view>
      </view>
    </view>

    <view class="footer safe-bottom">
      <!--
        用户端对 AI 画像唯一的可见入口。
        文案从用户视角出发（"记住的偏好"），不出现"画像"这种内部词。
        开关只关"回灌"：关掉后后台仍会继续沉淀，只是不再把历史偏好喂给 AI。
      -->
      <view class="memory-row">
        <text class="memory-text">使用 AI 记住的偏好</text>
        <view class="memory-toggle" :class="{ active: profileEnabled }" @tap="toggleProfileSwitch">
          <view class="memory-toggle-dot" />
        </view>
      </view>
      <view class="input-row">
        <view class="input-wrap">
          <input
            v-model="inputText"
            class="chat-input"
            placeholder="输入你的补充需求..."
            placeholder-class="input-placeholder"
            confirm-type="send"
            @confirm="sendMessage"
          />
        </view>
        <view class="send-btn" :class="{ 'is-stopping': loadingAi, 'has-text': !loadingAi && inputText.trim() }" @tap="loadingAi ? stopAiCall() : sendMessage()">
          <AppIcon v-if="!loadingAi" name="send" :size="30" color="#ffffff" />
          <view v-else class="stop-icon" />
        </view>
      </view>
      <button
        class="btn-black gen-btn"
        :class="{ disabled: !sessionId || finalizing || messages.length <= 1 }"
        :disabled="!sessionId || finalizing || messages.length <= 1"
        @tap="goItinerary"
      >
        {{ finalizing ? '正在生成行程计划...' : (sessionId ? '生成行程计划' : '请先发送一条消息') }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { useTripStore } from '@/store/trip'
import TripMap from '@/components/TripMap/TripMap.vue'
import type { TripMapSpot } from '@/components/TripMap/TripMap.vue'
import { geocodeSpotsSequential, planDrivingRoute, type GeoSpot } from '@/utils/geo'
import {
  chatWithAi,
  generateTravelPlan,
  getTravelHistory,
  mapPlanResponseToTripPlan,
  type PlanResponse,
  type ChatRequestParams,
  type BaseInfoParams,
  type ChatMessageVO,
  type AiChatResponse,
  type TripPlanFrame
} from '@/api/trip'
import { abortCurrentRequest } from '@/utils/request'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'
import { getProfileSwitch, setProfileSwitch } from '@/api/profile'
import { renderMarkdown } from '@/utils/markdown'
import {
  clearActiveChatSession,
  getActiveChatSession,
  setActiveChatSession
} from '@/utils/chatSession'

interface PlanPreview {
  title: string
  days: number
  totalSpots: number
  totalWalk: string
  daysPreview: { label: string; spots: string }[]
}

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  planPreview?: PlanPreview
  /** 行程生成后保存的 tripId，用于"查看详情"跳转 */
  tripId?: string
}

const tripStore = useTripStore()
const { currentTrip, currentAiResponse } = storeToRefs(tripStore)

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
// 导航栏固定高度（px，不随 rpx 缩放），避免 H5/大屏下内容被遮挡
const navHeight = statusBarHeight + 44

const inputText = ref('')
const scrollIntoView = ref('')
const loadingAi = ref(false)
const loadingText = ref('正在分析你的需求...')
const aiError = ref('')
// 最终生成的完整行程计划（含每日景点），用于预览卡片与映射 TripPlan
const aiPlanData = ref<PlanResponse | null>(null)
// 长对话会话id：首次为空，由后端创建并返回；后续多轮提交带上
const sessionId = ref<string>('')
/** 从行程列表/详情进入时的目标行程；为空表示新建规划 */
const selectedTripId = ref<string>('')

// 最近一次完整对话响应（含会话信息）
const aiChatResp = ref<AiChatResponse | null>(null)
// 是否正在生成"最终行程计划"（点击按钮后再综合上下文调一次）
const finalizing = ref(false)
// —— 生成中进度状态（异步任务：检索 → 框架 → 详情） ——
const genMessage = ref('正在准备生成…')
const genElapsed = ref(0)
const genFrame = ref<TripPlanFrame | null>(null)
const genCancelRequested = ref(false)
// —— 边生成边画：地图逐点绘制状态 ——
const genMapRef = ref<InstanceType<typeof TripMap> | null>(null)
const genMapDayIndex = ref(0)
const genDrawnCount = ref(0)
// 当前有效的逐点绘制任务令牌：每次启动新绘制递增，旧绘制的回调比对令牌即知是否被取代
let genMapSeqToken = 0
// 已对某天绘制过的景点名集合（避免切天重绘时重复）
const genDrawnDayKey = ref('')
// 对话历史（回传给后端做多轮记忆）
const chatHistory = ref<ChatMessageVO[]>([])
// 「使用 AI 记住的偏好」开关：默认开启；读取失败也保持开启，不打断对话
const profileEnabled = ref(true)

const messages = ref<ChatMessage[]>([getIntroMessage()])

/**
 * 刷新页面后恢复会话：凭本地持久化的 sessionId 拉取历史消息，
 * 重建对话气泡与 chatHistory，继续多轮上下文。
 */
async function restoreSession() {
  const sid = selectedTripId.value
    ? currentTrip.value?.chatSessionId || ''
    : currentTrip.value
      ? getActiveChatSession()
      : ''
  if (!currentTrip.value || (selectedTripId.value && !sid)) {
    clearActiveChatSession()
  }
  if (!sid || sessionId.value) return
  sessionId.value = sid
  try {
    const history = await getTravelHistory(sid)
    if (history && history.length > 0) {
      chatHistory.value = history
      messages.value = history.map((m) => ({
        role: m.role === 'user' ? 'user' : 'assistant',
        content: m.content
      }))
      scrollToBottom()
    }
  } catch (e) {
    console.warn('恢复会话历史失败:', e)
  }
}

onLoad(async (options?: Record<string, string>) => {
  // 对话链路已收回登录态：后端要按 user_id 沉淀画像，未登录直接去登录页（带 redirect 回跳）。
  // 对话页是 tabBar 之外的非 tab 页，登录成功后 redirectTo 会回到这里。
  if (!isLoggedIn()) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => redirectToLogin(), 400)
    return
  }
  loadProfileSwitch()
  selectedTripId.value = options?.tripId ? String(options.tripId) : ''
  if (selectedTripId.value) {
    try {
      await tripStore.getTripDetail(selectedTripId.value)
      messages.value = [getIntroMessage()]
    } catch (e) {
      console.warn('加载行程对话目标失败:', e)
      uni.showToast({ title: '行程加载失败', icon: 'none' })
      return
    }
  }
  await restoreSession()
})

function getIntroMessage(): ChatMessage {
  const t = currentTrip.value
  if (!t) {
    return { role: 'assistant', content: '你好！请先创建行程规划，我来帮你智能生成旅行计划。' }
  }
  const city = t.toCity || '目的地'
  const days = t.days ? `${t.days}天` : '1天'
  const prefs = t.tags?.join('、') || '经典必玩'
  return {
    role: 'assistant',
    content: `已收到你的需求：目的地${city}、${days}、${t.people || 2}人同行、偏好${prefs}。\n你可以继续补充偏好（如美食、自然风光、历史文化），或直接告诉我"生成行程"开始规划。`
  }
}

function buildBaseInfo(): BaseInfoParams | null {
  const t = currentTrip.value
  if (!t) return null
  return {
    departure_city: t.fromCity || '',
    destination_city: t.toCity || '',
    start_day: t.startDate || '',
    end_date: t.endDate || '',
    days: t.days || 1,
    hobby: t.tags || [],
    people_num: String(t.people || 2),
    budget: String(t.budget || 3000),
    // 外部带入的行程上下文（如游记/景点），透传给智能体作为首轮提示词
    context_note: t.contextNote || ''
  }
}

function buildChatRequest(userInput: string): ChatRequestParams | null {
  const baseInfo = buildBaseInfo()
  if (!baseInfo) return null
  return {
    session_id: sessionId.value || undefined,
    user_input: userInput,
    base_info: baseInfo,
    chat_history: chatHistory.value
  }
}

/** 预算约束：负数预算不允许进入智能规划 */
function validateTripBudget(): boolean {
  const t = currentTrip.value
  if (t && Number(t.budget) < 0) {
    uni.showToast({ title: '预算不能为负数', icon: 'none' })
    return false
  }
  return true
}

function buildPlanPreview(response: PlanResponse): PlanPreview {
  const plan = response.plan_data
  if (!plan) {
    return { title: '生成中...', days: 0, totalSpots: 0, totalWalk: '', daysPreview: [] }
  }
  let totalSpots = 0
  const daysPreview = (plan.day_list || []).map((day, i) => {
    const spots = (day.schedule || []).map((s) => s.spot_name).join(' → ')
    totalSpots += day.total_spot || (day.schedule || []).length
    return { label: `第${i + 1}天：${day.date || ''}`, spots: spots || '待安排' }
  })
  return {
    title: plan.title || '智能规划行程',
    days: daysPreview.length || 1,
    totalSpots,
    totalWalk: plan.total_walk || '',
    daysPreview
  }
}

/**
 * 多轮对话：调用 /travel/chat，拿到 AI 回复文本并展示在气泡。
 * @param userInput 本轮用户输入
 */
async function callAiChat(userInput: string) {
  // 预算约束：负数预算不允许进入智能规划
  if (!validateTripBudget()) return

  const params = buildChatRequest(userInput)
  if (!params) {
    aiError.value = '请先填写行程信息'
    return
  }

  loadingAi.value = true
  aiError.value = ''

  const tips = ['正在分析你的需求...', '正在匹配最佳景点...', '正在规划每日行程...', '正在优化游玩路线...']
  let tipIdx = 0
  const tipTimer = setInterval(() => {
    tipIdx = (tipIdx + 1) % tips.length
    loadingText.value = tips[tipIdx]
  }, 2000)

  try {
    const response: AiChatResponse = await chatWithAi(params)
    sessionId.value = response.session_id
    // 当前会话只在本地作为活动规划保存；指定行程还要写入行程自身关联字段。
    setActiveChatSession(response.session_id)
    if (currentTrip.value && !currentTrip.value.chatSessionId) {
      currentTrip.value.chatSessionId = response.session_id
    }
    aiChatResp.value = response
    // 更新对话历史（后端返回的是含本轮 user + assistant 的完整历史）
    chatHistory.value = response.chat_history || []

    // 把 AI 回复作为气泡展示
    messages.value.push({
      role: 'assistant',
      content: response.reply || '（AI 暂无回复）'
    })
  } catch (err: any) {
    console.error('AI 对话失败:', err)
    // 用户点击暂停中止请求：不算错误，提示"已停止"并恢复输入
    if (String(err?.errMsg || err?.message || '').includes('abort')) {
      messages.value.push({ role: 'assistant', content: '⏸ 已停止生成，你可以继续补充需求。' })
    } else {
      aiError.value = err?.data?.msg || err?.message || 'AI 服务暂时不可用，请稍后重试'
    }
  } finally {
    clearInterval(tipTimer)
    loadingAi.value = false
    scrollToBottom()
  }
}

/** 暂停/停止当前 AI 生成（中止进行中的请求） */
function stopAiCall() {
  abortCurrentRequest()
  uni.showToast({ title: '正在停止...', icon: 'none' })
}

function retryAiCall() {
  // 重试：重新发送上一条用户消息
  const lastUserMsg = [...messages.value].reverse().find((m) => m.role === 'user')
  callAiChat(lastUserMsg?.content || '')
}

function scrollToBottom() {
  nextTick(() => {
    scrollIntoView.value = 'msg-' + messages.value.length
  })
}

// loading 气泡插入消息流后，自动滚动到其位置（即用户消息下方）
watch(loadingAi, (val) => {
  if (val) scrollToBottom()
})

async function sendMessage() {
  // AI 生成期间不允许发送下一条需求（按钮已变为暂停）
  if (loadingAi.value) {
    uni.showToast({ title: 'AI 正在生成中，请稍候', icon: 'none' })
    return
  }
  if (!ensureLogin()) return
  const text = inputText.value.trim()
  if (!text) return
  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  scrollToBottom()

  // 真实多轮调用：带上本轮 user_input，后端会把历史上下文一并提交给智能体
  await callAiChat(text)
}

/** 登录拦截：对话/生成链路需要登录，未登录先去登录页（带 redirect 回跳） */
function ensureLogin(): boolean {
  if (isLoggedIn()) return true
  uni.showToast({ title: '请先登录', icon: 'none' })
  setTimeout(() => redirectToLogin(), 400)
  return false
}

/** 读取「使用 AI 记住的偏好」开关：失败保持默认开启，不打断对话 */
async function loadProfileSwitch() {
  if (!isLoggedIn()) return
  try {
    const res = await getProfileSwitch()
    if (res && typeof res.enabled === 'boolean') {
      profileEnabled.value = res.enabled
    }
  } catch (e) {
    console.warn('读取偏好记忆开关失败，保持默认开启:', e)
  }
}

/** 切换偏好记忆开关：先乐观更新 UI，失败回滚 */
async function toggleProfileSwitch() {
  if (!ensureLogin()) return
  const next = !profileEnabled.value
  profileEnabled.value = next
  try {
    const res = await setProfileSwitch(next)
    if (res && typeof res.enabled === 'boolean') {
      profileEnabled.value = res.enabled
    }
    uni.showToast({
      title: next ? '已开启，AI 会延续你的偏好' : '已关闭，AI 会重新询问你的偏好',
      icon: 'none'
    })
  } catch (e) {
    profileEnabled.value = !next
    console.warn('保存偏好记忆开关失败，已回滚:', e)
  }
}

function goBack() {
  uni.navigateBack()
}

function onDraft() {
  uni.showToast({ title: '草稿已保存', icon: 'success' })
}

/**
 * 点击"生成行程计划"：提交异步任务 → 轮询阶段进度（检索/框架/细化）→
 * 完成前实时展示骨架预览与已用时，支持中途取消；
 * 同一行程命中缓存时直接秒开。
 */
async function goItinerary() {
  if (!ensureLogin()) return
  if (!sessionId.value) {
    uni.showToast({ title: '请先发送消息与AI对话', icon: 'none' })
    return
  }
  if (finalizing.value) return
  // 预算约束：负数预算不允许进入智能规划
  if (!validateTripBudget()) return

  const params = buildChatRequest('请综合以上所有讨论内容，生成最终的完整旅游行程计划')
  if (!params) {
    uni.showToast({ title: '请先填写行程信息', icon: 'none' })
    return
  }

  finalizing.value = true
  genCancelRequested.value = false
  genFrame.value = null
  genDrawnCount.value = 0
  genDrawnDayKey.value = ''
  genMapDayIndex.value = 0
  genMapSeqToken++
  genMessage.value = '正在提交生成任务…'
  genElapsed.value = 0
  const startAt = Date.now()
  // 本地秒表：即使轮询慢也保持"已等待"跳动
  const elapsedTimer = setInterval(() => {
    genElapsed.value = Math.round((Date.now() - startAt) / 1000)
  }, 1000)

  try {
    const result = await generateTravelPlan(params, {
      onProgress: (p) => {
        genMessage.value = p.message
        if (p.elapsedSec) genElapsed.value = p.elapsedSec
        if (p.frame) genFrame.value = p.frame
      },
      shouldCancel: () => genCancelRequested.value
    })

    const finalPlan: PlanResponse = result.plan
    aiPlanData.value = finalPlan
    currentAiResponse.value = finalPlan

    if (result.fromCache) {
      uni.showToast({ title: '命中缓存，已为你秒开 ⚡', icon: 'none' })
    }

    // detail 完成：用精确 location 重新地理编码，把遮罩内地图更新为精确路线
    drawDetailDayOnMap(finalPlan, genMapDayIndex.value)

    // 将最终 AI 响应映射为 TripPlan 并存储
    const tripParams = {
      fromCity: currentTrip.value?.fromCity || '',
      toCity: currentTrip.value?.toCity || '',
      tags: currentTrip.value?.tags || []
    }
    const mappedTrip = mapPlanResponseToTripPlan(finalPlan, tripParams)
    // 将会话和最终行程绑定，之后只能从该行程入口恢复对应对话。
    mappedTrip.chatSessionId = sessionId.value
    // 保留原始预算和人数信息
    if (currentTrip.value) {
      mappedTrip.budget = currentTrip.value.budget
      mappedTrip.people = currentTrip.value.people
      mappedTrip.startDate = currentTrip.value.startDate
      mappedTrip.endDate = currentTrip.value.endDate
      mappedTrip.fromCity = currentTrip.value.fromCity
    }
    currentTrip.value = mappedTrip

    // 入库：保存到"我的行程"，拿到后端 id 供"查看详情"跳转
    let tripId = ''
    try {
      const saved = await tripStore.saveTrip(mappedTrip)
      tripId = String(saved?.id ?? '')
      currentTrip.value = saved
    } catch (saveErr) {
      console.warn('行程入库失败，仍可预览（未持久化）:', saveErr)
      // 入库失败不阻断预览，但提示用户
      uni.showToast({ title: '行程已生成，但保存失败，请稍后在详情页重试保存', icon: 'none', duration: 2500 })
    }

    // 在对话区展示最终行程预览卡片（含"查看详情"入口，进地图路线详情页）
    const preview = buildPlanPreview(finalPlan)
    const summary = `✨ 已为你生成「${preview.title}」！\n\n📋 ${preview.days}天行程，共${preview.totalSpots}个景点：\n${preview.daysPreview.map((d) => `  ${d.label}\n    ${d.spots}`).join('\n')}`
    messages.value.push({
      role: 'assistant',
      content: summary,
      planPreview: preview,
      // 入库失败时用空 id 兜底（详情页无 id 时用 currentTrip 预览）
      tripId: tripId || ''
    })
    scrollToBottom()

    // 不再自动跳转详情页：生成完成后在预览卡片下方提供"查看详情"入口，
    // 详情页内可查看地图路线（每天一色、日期切换聚焦、总览完整路线）
  } catch (err: any) {
    console.error('生成最终行程失败:', err)
    const msg = String(err?.errMsg || err?.message || '')
    // 用户取消
    if (msg.includes('CANCELED') || msg.includes('已取消')) {
      uni.showToast({ title: '已取消生成，可随时重新生成', icon: 'none', duration: 2000 })
    }
    // 超时（agent 生成慢）给友好提示，避免显示原始错误串
    else if (msg.includes('timeout') || msg.includes('超时')) {
      uni.showToast({ title: '生成超时，AI 服务繁忙，请稍后重试', icon: 'none', duration: 3000 })
    } else {
      uni.showToast({ title: err?.data?.msg || msg || '生成最终行程失败', icon: 'none' })
    }
  } finally {
    clearInterval(elapsedTimer)
    finalizing.value = false
  }
}

/** 取消生成（轮询循环检测到后调用 /travel/plan/cancel） */
function cancelGeneration() {
  if (!finalizing.value) return
  genCancelRequested.value = true
  genMapSeqToken++
  genMessage.value = '正在取消…'
}

/** 从生成结果预览卡片进入详情页（含地图路线） */
function goPlanDetail(tripId: string) {
  uni.navigateTo({ url: tripId ? `/pages/trip/detail?id=${tripId}` : '/pages/trip/detail' })
}

/** 生成遮罩内地图：点击标记点的回调（暂仅提示，可扩展高亮骨架对应行） */
function onGenMapSpotTap(_spot: TripMapSpot, _index: number) {
  // 预留：点击地图标记可滚动到骨架预览对应天/点
}

/** 切换遮罩内地图显示的天 */
function switchGenMapDay(delta: number) {
  const total = genFrame.value?.day_list?.length || 0
  if (total <= 1) return
  let next = genMapDayIndex.value + delta
  if (next < 0) next = total - 1
  if (next >= total) next = 0
  genMapDayIndex.value = next
  drawFrameDayOnMap(next)
}

/**
 * 对 frame 某一天的景点名逐个地理编码并追加到地图，
 * 形成"逐景点边生成边画"的动画效果。
 * frame 只有景点名，故用"景点名 + 目的地城市"做地理编码。
 */
async function drawFrameDayOnMap(dayIndex: number) {
  const frame = genFrame.value
  if (!frame || !frame.day_list?.length) return
  const day = frame.day_list[dayIndex]
  if (!day) return

  const dayKey = `${frame.title}|${dayIndex}`
  // 已绘制过同一天则不重复
  if (genDrawnDayKey.value === dayKey) return

  // 启动本轮绘制，令牌递增使上一轮回调失效
  const myToken = ++genMapSeqToken
  const outdated = () => myToken !== genMapSeqToken

  // 等待组件实例就绪（遮罩刚渲染时 ref 可能未挂载）
  await nextTick()
  const mapRef = genMapRef.value
  if (!mapRef || outdated()) return
  mapRef.clearSpots()
  genDrawnCount.value = 0
  genDrawnDayKey.value = dayKey

  const city = currentTrip.value?.toCity || ''
  const spots: GeoSpot[] = (day.spot_names || []).map((name) => ({ name, city }))
  if (!spots.length) return

  // 逐点地理编码 + 追加绘制（~350ms 间隔）
  await geocodeSpotsSequential(
    spots,
    (spot) => {
      if (outdated()) return
      mapRef.appendSpot({ name: spot.name, lat: spot.lat, lng: spot.lng } as TripMapSpot)
      genDrawnCount.value++
    },
    350
  )
}

/**
 * detail 阶段完成：用精确 location 地址重新地理编码，
 * 把地图更新为精确路线（覆盖 frame 阶段的景点名定位）。
 */
async function drawDetailDayOnMap(plan: PlanResponse, dayIndex: number) {
  const dayList = plan.plan_data?.day_list || []
  const day = dayList[dayIndex]
  if (!day) return
  await nextTick()
  const mapRef = genMapRef.value
  if (!mapRef) return

  // 取代 frame 阶段的绘制
  const myToken = ++genMapSeqToken
  const outdated = () => myToken !== genMapSeqToken

  mapRef.clearSpots()
  genDrawnCount.value = 0
  genDrawnDayKey.value = `detail|${dayIndex}`
  const city = currentTrip.value?.toCity || ''
  const spots: GeoSpot[] = (day.schedule || []).map((s) => ({
    name: s.spot_name,
    address: s.location,
    city
  }))
  if (!spots.length) return

  // detail 已是最终结果，逐点动画节奏稍快
  await geocodeSpotsSequential(
    spots,
    (spot) => {
      if (outdated()) return
      mapRef.appendSpot({
        name: spot.name,
        address: spot.address,
        lat: spot.lat,
        lng: spot.lng
      } as TripMapSpot)
      genDrawnCount.value++
    },
    200
  )

  // 全部定位完成后：规划真实驾车路径（默认打车模式），遮罩内地图从直线升级为沿道路路径
  if (outdated()) return
  const drawnSpots = genDrawnCount.value > 0 ? mapRef.getSpots() : []
  if (drawnSpots.length >= 2) {
    const path = await planDrivingRoute(drawnSpots)
    if (outdated() || !path) return
    mapRef.setRoutes([
      { color: '#14b8a6', label: `第${genMapDayIndex.value + 1}天 · `, spots: drawnSpots, path }
    ])
  }
}

/** 当 frame 到达/天切换时触发逐点绘制 */
watch(genFrame, (frame) => {
  if (frame && frame.day_list?.length) {
    genMapDayIndex.value = 0
    drawFrameDayOnMap(0)
  }
})
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg-page);
  display: flex;
  flex-direction: column;
}

.nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border);
  box-sizing: border-box;
}

.nav-inner {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 32rpx;
}

.back {
  display: flex;
  align-items: center;
  margin-right: 16rpx;
  padding: 8rpx;
}

.nav-title {
  flex: 1;
  font-size: var(--fs-subhead);
  font-weight: 700;
  color: var(--text-main);
}

.draft-btn {
  font-size: var(--fs-body);
  color: $mint-primary;
  font-weight: 500;
}

.chat-scroll {
  flex: 1;
  min-height: 0;
  height: auto;
  padding: 24rpx 32rpx;
  box-sizing: border-box;
}

.msg-row {
  display: flex;
  margin-bottom: 32rpx;
  gap: 16rpx;

  &.user {
    justify-content: flex-end;
  }
}

.avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: $mint-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai-avatar {
  background: linear-gradient(135deg, $mint-primary, $secondary-color);
  box-shadow: 0 4rpx 12rpx rgba(20, 184, 166, 0.3);
}

.bubble {
  max-width: 82%;
  padding: 20rpx 28rpx;
  border-radius: 32rpx;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.06);

  &.assistant {
    background: var(--bg-bubble);
    color: var(--text-main);
    border-top-left-radius: 8rpx;
  }

  &.user {
    background: $mint-primary;
    color: #fff;
    border-top-right-radius: 8rpx;
  }
}

.role-tag {
  font-size: var(--fs-caption);
  font-weight: 700;
  display: block;
  margin-bottom: 8rpx;
  opacity: 0.7;

  &.user-tag {
    text-align: right;
    opacity: 0.9;
  }
}

.msg-text {
  font-size: var(--fs-body);
  line-height: 1.55;
  display: block;
  white-space: pre-wrap;
}

/* AI 回复 Markdown 渲染内容（mp-html）样式适配气泡 */
.msg-md {
  font-size: var(--fs-body);
  line-height: 1.55;
  color: var(--text-main);

  :deep(p) {
    margin: 0 0 12rpx;
    &:last-child {
      margin-bottom: 0;
    }
  }
  :deep(h1), :deep(h2), :deep(h3), :deep(h4) {
    font-size: var(--fs-title);
    font-weight: 700;
    margin: 16rpx 0 8rpx;
    line-height: 1.4;
  }
  :deep(strong) {
    font-weight: 700;
  }
  :deep(ul), :deep(ol) {
    padding-left: 36rpx;
    margin: 8rpx 0 12rpx;
  }
  :deep(li) {
    margin-bottom: 6rpx;
  }
  :deep(code) {
    background: rgba(0, 0, 0, 0.06);
    border-radius: 6rpx;
    padding: 0 8rpx;
    font-size: var(--fs-meta);
  }
  :deep(pre) {
    background: rgba(0, 0, 0, 0.06);
    border-radius: 12rpx;
    padding: 16rpx;
    overflow-x: auto;
    margin: 12rpx 0;
  }
  :deep(blockquote) {
    border-left: 6rpx solid rgba(0, 0, 0, 0.15);
    padding-left: 16rpx;
    margin: 12rpx 0;
    opacity: 0.85;
  }
  :deep(a) {
    color: $primary-color;
    text-decoration: underline;
  }
}

/* loading 动画 */
.thinking-bubble {
  min-width: 200rpx;
}

.thinking-dots {
  display: flex;
  gap: 10rpx;
  margin: 16rpx 0 8rpx;
}

.dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: $mint-primary;
  animation: dotPulse 1.4s ease-in-out infinite;

  &:nth-child(2) {
    animation-delay: 0.2s;
  }

  &:nth-child(3) {
    animation-delay: 0.4s;
  }
}

@keyframes dotPulse {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}

.thinking-text {
  font-size: var(--fs-meta);
  color: var(--text-secondary);
}

/* 错误提示 */
.error-card {
  background: var(--error-bg);
  border: 1rpx solid var(--error-border);
  border-radius: 20rpx;
  padding: 32rpx;
  text-align: center;
  margin-bottom: 32rpx;
}

.error-icon {
  display: flex;
  justify-content: center;
  margin-bottom: 12rpx;
}

.error-text {
  font-size: var(--fs-body);
  color: var(--danger);
  display: block;
  margin-bottom: 20rpx;
}

.retry-btn {
  background: $mint-primary;
  color: #fff;
  padding: 14rpx 40rpx;
  border-radius: 999rpx;
  font-size: var(--fs-body);
  display: inline-block;
}

/* AI 行程预览卡片 */
.plan-preview-card {
  margin-top: 20rpx;
  padding: 20rpx;
  background: var(--bg-card);
  border-radius: 16rpx;
  border: 1rpx solid rgba(168, 230, 207, 0.3);
}

.plan-title {
  font-size: var(--fs-title);
  font-weight: 700;
  color: var(--text-main);
  display: block;
  margin-bottom: 12rpx;
}

.plan-stats {
  display: flex;
  gap: 20rpx;
  margin-bottom: 16rpx;
}

.stat {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  font-size: var(--fs-meta);
  color: $mint-primary;
  background: rgba(168, 230, 207, 0.15);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.day-list {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.day-row {
  display: flex;
  align-items: flex-start;
  gap: 10rpx;
}

.day-label {
  font-size: var(--fs-meta);
  color: var(--text-main);
  font-weight: 600;
  flex-shrink: 0;
  min-width: 140rpx;
}

.day-spots {
  font-size: var(--fs-meta);
  color: var(--text-secondary);
  flex: 1;
  line-height: 1.4;
}

/* 生成完成后的"查看详情"按钮（进入地图路线详情页） */
.detail-btn {
  margin-top: 20rpx;
  width: 100%;
  font-size: var(--fs-body);
  border-radius: 999rpx;
}

/* 「使用 AI 记住的偏好」开关：靠右、缩放到与文字同高，不抢输入框的注意力 */
.memory-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.memory-text {
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
}

.memory-toggle {
  width: 56rpx;
  height: 32rpx;
  border-radius: 999rpx;
  background: var(--bg-muted);
  border: 1rpx solid var(--border);
  position: relative;
  transition: background 0.2s ease;

  &.active {
    background: $mint-primary;
    border-color: $mint-primary;

    .memory-toggle-dot {
      transform: translateX(26rpx);
    }
  }
}

.memory-toggle-dot {
  position: absolute;
  top: 2rpx;
  left: 2rpx;
  width: 26rpx;
  height: 26rpx;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.15);
  transition: transform 0.2s ease;
}

/* 底部 */
.footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--bg-card);
  border-top: 1rpx solid var(--border);
  padding: 24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
}

.input-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.input-wrap {
  flex: 1;
  height: 88rpx;
  background: var(--bg-input);
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  padding: 0 32rpx;
}

.chat-input {
  flex: 1;
  font-size: var(--fs-body);
  color: var(--text-main);
}

:deep(.input-placeholder) {
  color: var(--text-placeholder);
}

.send-btn {
  width: 88rpx;
  height: 88rpx;
  background: $mint-primary;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(168, 230, 207, 0.4);
  transition: all 0.2s ease;

  // 输入框有内容：深青色高亮，提示可发送
  &.has-text {
    background: $secondary-color;
    box-shadow: 0 8rpx 24rpx rgba(20, 184, 166, 0.45);
  }

  // AI 生成中：变为"暂停"按钮（红色），中间为白色正方形
  &.is-stopping {
    background: linear-gradient(135deg, #f7897d, #ef5b5b);
    box-shadow: 0 8rpx 24rpx rgba(239, 91, 91, 0.35);
    animation: pulse-stop 1.2s ease-in-out infinite;
  }

  &:active {
    transform: scale(0.92);
  }
}

// 暂停图标：白色正方形，居中对齐由 send-btn 的 flex 布局保证
.stop-icon {
  width: 30rpx;
  height: 30rpx;
  background: #ffffff;
  border-radius: 6rpx;
  flex-shrink: 0;
}

@keyframes pulse-stop {
  0%, 100% { box-shadow: 0 8rpx 24rpx rgba(239, 91, 91, 0.3); }
  50% { box-shadow: 0 8rpx 32rpx rgba(239, 91, 91, 0.55); }
}

.gen-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  font-size: var(--fs-body);

  &.disabled {
    opacity: 0.4;
    pointer-events: none;
  }

  // 已具备生成条件（聊过一轮、有会话）：深绿色高亮，提示可点击生成
  &:not(.disabled) {
    background: linear-gradient(90deg, #48bb88, #1f7a54);
    box-shadow: 0 8rpx 24rpx rgba(31, 122, 84, 0.3);
  }
}

/* 生成行程计划：进度遮罩 */
.gen-overlay {
  position: fixed;
  inset: 0;
  z-index: 999;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 48rpx;
}

.gen-card {
  width: 100%;
  max-height: 70vh;
  overflow-y: auto;
  background: var(--bg-card);
  border-radius: 28rpx;
  padding: 48rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.gen-spinner {
  width: 72rpx;
  height: 72rpx;
  border: 6rpx solid var(--border);
  border-top-color: $mint-primary;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.gen-title {
  margin-top: 28rpx;
  font-size: var(--fs-title);
  font-weight: 700;
  color: var(--text-main);
}

.gen-stage {
  margin-top: 12rpx;
  font-size: var(--fs-body);
  color: var(--text-secondary);
  text-align: center;
}

.gen-elapsed {
  margin-top: 8rpx;
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
}

/* 骨架预览（阶段一产物） */
.gen-frame {
  margin-top: 28rpx;
  width: 100%;
  background: var(--bg-page);
  border: 1rpx solid var(--border);
  border-radius: 20rpx;
  padding: 24rpx;
  box-sizing: border-box;
}

.gen-frame-title {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: var(--fs-body);
  font-weight: 700;
  color: var(--text-main);
  margin-bottom: 16rpx;
}

.gen-frame-day {
  margin-bottom: 14rpx;
}

.gen-frame-label {
  font-size: var(--fs-meta);
  font-weight: 600;
  color: $mint-primary;
  display: block;
  margin-bottom: 4rpx;
}

.gen-frame-spots {
  font-size: var(--fs-meta);
  color: var(--text-body);
  line-height: 1.5;
}

.gen-frame-tip {
  display: block;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx dashed var(--border);
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
}

/* 遮罩内：边生成边画的路线地图 */
.gen-map-wrap {
  margin-top: 24rpx;
  width: 100%;
}

.gen-map-day {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12rpx;
  padding: 0 8rpx;
}

.gen-map-day-label {
  font-size: var(--fs-meta);
  color: var(--text-secondary);
}

.gen-map-day-switch {
  display: flex;
  gap: 24rpx;
}

.gen-map-arrow {
  font-size: var(--fs-subhead);
  color: $mint-primary;
  width: 48rpx;
  text-align: center;
  line-height: 1;
}

.gen-cancel {
  margin-top: 32rpx;
  font-size: var(--fs-body);
  color: var(--danger);
  padding: 12rpx 40rpx;
  border: 2rpx solid var(--danger);
  border-radius: 999rpx;
}

.gen-cancel:active {
  opacity: 0.7;
}
</style>
