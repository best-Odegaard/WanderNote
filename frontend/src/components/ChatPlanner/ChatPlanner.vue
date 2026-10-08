<template>
  <view class="page" :class="{ 'as-tab': tabMode }">
    <view class="nav" :style="{ paddingTop: statusBarHeight + 'px', height: navHeight + 'px' }">
      <view class="nav-inner">
        <!-- tab 模式（首页）没有上一页可返回，不显示返回键，避免按了没反应 -->
        <view v-if="!tabMode" class="back" @tap="goBack">
          <AppIcon name="chevron-left" :size="34" color="var(--text-body)" />
        </view>
        <text class="nav-title">小笺</text>
        <view class="nav-actions">
          <!--
            精细设置入口。
            旧的表单式规划（wizard）不再出现在主路径上，但仍有用户想自己填具体日期、
            人数、预算，所以留一个入口，而不是把那条路彻底砍掉。

            「生成行程计划」原来挂在顶栏，已按设计稿挪到输入区下方的常驻大按钮
            （见页脚末尾），这里只留次要入口。
          -->
          <text class="nav-text-btn plain" @tap="goWizard">精细设置</text>
        </view>
      </view>
    </view>

    <!--
      paddingTop 用内联而不是 CSS：导航是 fixed，滚动区要按它的实际高度避让。
      这里额外 +16px —— 只避让导航高度的话，第一条消息（连同 AI 头像）会紧贴导航底边，
      看起来像被切掉；CSS 里的 padding-top 已经被这个内联样式覆盖，所以间距只能加在这里。
    -->
    <scroll-view
      scroll-y
      class="chat-scroll"
      :style="{ paddingTop: (navHeight + 16) + 'px' }"
      :scroll-into-view="scrollIntoView"
      scroll-with-animation
    >
      <!--
        Mock 模式告警条。
        这条不是装饰：曾经因为 .env 里 VITE_USE_MOCK=true，整条 AI 链路返回的是**写死的假回复**，
        界面上既没有可选项也没有「猜你想问」，看起来就像"AI 不理会我说的话"，
        实际排查了很久才发现根本没连后端。把它显式挂在界面上，以后一眼可见。
      -->
      <view v-if="useMock" class="mock-bar">
        <text class="mock-text">⚠️ 当前是本地 Mock 数据：AI 回复与行程都是假的，未连接后端</text>
      </view>
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
          <view v-if="msg.role === 'assistant'" class="msg-md" v-html="renderMarkdown(displayText(msg.content))" />
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

      <!--
        可点选项区。两种来源：
          · 开场三选一（本地的 ENTRY_QUESTION）—— 让用户不用对着空白输入框凭空想
          · 槽位问题（后端槽位引擎下发）—— 点一下即完成一轮信息收集，不调用大模型
        同一时刻只展示一个，避免一次抛两个问题给用户。
      -->
      <ChatChips
        v-if="visibleQuestion && !finalizing"
        :question="visibleQuestion"
        :disabled="slotSubmitting || loadingAi || locating"
        @select="onChipSelect"
        @skip="onChipSkip"
      />

      <!--
        「猜你想问」：本轮之后推荐的追问。点一下就把那句话发出去，
        用户不必自己组织语言 —— 这是"每轮都给几个能点的"落点。
      -->
      <SuggestedQuestions
        v-if="suggestedQuestions.length > 0 && !finalizing"
        :questions="suggestedQuestions"
        :disabled="slotSubmitting || loadingAi || locating"
        @pick="onSuggestPick"
      />

      <view :id="'msg-' + messages.length" />
      <!-- 底部留白：footer 里除了输入区还多了一个 96rpx 的「生成行程计划」大按钮 -->
      <view style="height: 460rpx" />
    </scroll-view>

      <!-- 生成行程计划进度遮罩（异步任务：检索 → 框架 → 详情，可取消） -->
    <transition name="fb-fade">
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
    </transition>

    <view class="footer safe-bottom" :class="{ 'tab-mode': tabMode }">
      <!--
        需求完整度 + 下一步提示。
        进度条的作用是给用户「再点两下就能出完整方案」的预期，
        避免点击式追问显得没完没了。
      -->
      <view v-if="answeredCount > 0" class="slot-bar">
        <SlotProgress :completeness="completeness" />
      </view>

      <!--
        完整度浮出工具条（设计稿 3.3）：≥50% 出「选酒店」，≥60% 且已知住宿偏好出「生成完整路线」。
        为什么必须有这个入口：底部只有一个「生成行程计划」，用户生成完行程就再也找不到
        「晚上住哪」这一步，而住宿决定了当天路线能不能闭环（见 pages/trip/route.vue 的 resolveHotel）——
        后端 ready.hotel 早就把这个能力位留好了（SlotReadyVO.hotel），前端一直没用。
      -->
      <view v-if="showToolbar" class="chat-toolbar">
        <view v-if="hotelEntryReady" class="tool-btn" @tap="onPickHotel">
          🏨 {{ hotelEntryLabel }}
        </view>
        <view v-if="routeEntryReady" class="tool-btn primary" @tap="onFullRoute">🗺️ 生成完整路线</view>
      </view>

      <!--
        底部输入区。
        原来这里有一个「使用 AI 记住的偏好」开关，现已按产品要求隐藏并停用：
        画像回灌由后端 sky.profile.inject-enabled 控制（默认 false），
        恢复时改配置即可，不需要把这段 UI 加回来。
      -->
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

      <!--
        底部常驻「生成行程计划」（设计稿位置：输入区下方、整行宽）。
        信息够了（目的地 + 天数已知，与后端 ready.frame 一致）时高亮，提示已经可以点了。
        动作仍是原来的 onDrawRoute —— 只挪了按钮位置，生成链路没动。
      -->
      <view class="gen-btn" :class="{ ready: canDraw }" @tap="onDrawRoute">
        <AppIcon name="sparkles" :size="32" color="var(--on-brand)" />
        <text class="gen-btn-text">生成行程计划</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, nextTick, watch, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useTripStore } from '@/store/trip'
import TripMap from '@/components/TripMap/TripMap.vue'
import type { TripMapSpot } from '@/components/TripMap/TripMap.vue'
import ChatChips from '@/components/ChatChips/ChatChips.vue'
import SuggestedQuestions from '@/components/SuggestedQuestions/SuggestedQuestions.vue'
import SlotProgress from '@/components/SlotProgress/SlotProgress.vue'
import { geocodeSpotsSequential, locateCurrentCity, planDrivingRoute, type GeoSpot } from '@/utils/geo'
import {
  chatWithAi,
  chatWithAiStream,
  canStreamChat,
  generateTravelPlan,
  getTravelHistory,
  mapPlanResponseToTripPlan,
  submitSlotAnswer,
  getSlotState,
  type PlanResponse,
  type ChatRequestParams,
  type BaseInfoParams,
  type ChatMessageVO,
  type AiChatResponse,
  type SlotQuestion,
  type TripPlanFrame
} from '@/api/trip'
import { abortRequest } from '@/utils/request'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'
import { USE_MOCK, FOLLOWUP_MARK_OPEN } from '@/utils/constant'
import { renderMarkdown } from '@/utils/markdown'
import {
  clearActiveChatSession,
  getActiveChatSession,
  setActiveChatSession
} from '@/utils/chatSession'
import { showToast } from '@/utils/feedback'

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

/** 是否处于本地 Mock 模式（界面要显式提示，否则会被误当成真实 AI 回复） */
const useMock = USE_MOCK

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
const messages = ref<ChatMessage[]>([getIntroMessage()])

/**
 * 刷新页面后恢复会话：凭本地持久化的 sessionId 拉取历史消息，
 * 重建对话气泡与 chatHistory，继续多轮上下文。
 */
async function restoreSession() {
  // 冷启动（没有 currentTrip）也要认这个「进行中的会话」：
  // 否则用户刷新一次页面，刚通过选项答过的进度就全丢了。
  const sid = selectedTripId.value
    ? currentTrip.value?.chatSessionId || ''
    : getActiveChatSession()
  if (selectedTripId.value && !sid) {
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

/**
 * 恢复槽位进度。
 *
 * 槽位快照由后端槽位引擎按 sessionId 持有，页面刷新后前端是空的 ——
 * 不拉回来的话，chips 和进度条会凭空消失，但用户明明已经答过好几题。
 */
async function restoreSlotState() {
  if (!sessionId.value) return
  try {
    tripStore.setSlotState(await getSlotState(sessionId.value))
  } catch (e) {
    console.warn('恢复槽位进度失败:', e)
  }
}

/**
 * 组件入参。
 *
 * 这个组件同时被两个入口使用：
 *   · pages/home/index.vue —— tabBar 首页，进入即是对话（tabMode=true，无返回键、底部让开 TabBar）
 *   · pages/ai/chat.vue    —— 从行程列表/详情带上下文进来的独立对话页
 * 所有对话逻辑只此一份，避免两个入口各写一套后行为不一致。
 */
const props = withDefaults(
  defineProps<{
    /** tabBar 页模式：首页 */
    tabMode?: boolean
    /** 指定行程 id：从「进入行程对话」进来时带上 */
    tripId?: string
    /** 入口带过来的首句话（首页搜索框 / 探索页 / 景点详情） */
    initialQuery?: string
  }>(),
  { tabMode: false, tripId: '', initialQuery: '' }
)

/**
 * 初始化。
 *
 * 原来是页面的 onLoad，搬进组件后改成显式调用：
 * 组件的 mounted 早于页面拿到 query 参数，用参数驱动 + 一次性守卫最稳。
 */
let inited = false
async function initPlanner() {
  if (inited) return
  inited = true

  // 入参兜底：既支持父页面用 props 传，也支持直接读当前页的 query。
  // 组件的 onMounted 早于页面 onLoad 回调时 props 还是空的，只认 props 会丢掉 tripId/q；
  // 读 getCurrentPages().options 与行程详情页用的是同一套做法，不依赖时序。
  const page = getCurrentPages()[getCurrentPages().length - 1] as
    | { options?: Record<string, string> }
    | undefined
  const opt = page?.options || {}
  const wantTripId = props.tripId || (opt.tripId ? String(opt.tripId) : '')
  const wantQuery =
    props.initialQuery || (opt.q ? decodeURIComponent(String(opt.q)) : '').trim()

  // 对话链路已收回登录态：后端要按 user_id 沉淀画像，未登录直接去登录页（带 redirect 回跳）。
  // 对话页是 tabBar 之外的非 tab 页，登录成功后 redirectTo 会回到这里。
  // redirectToLogin 会把当前页的 query（含 q）一起带上，所以登录不会丢掉用户输入。
  if (!isLoggedIn()) {
    showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => redirectToLogin(), 400)
    return
  }
  selectedTripId.value = wantTripId
  if (selectedTripId.value) {
    try {
      await tripStore.getTripDetail(selectedTripId.value)
      messages.value = [getIntroMessage()]
    } catch (e) {
      console.warn('加载行程对话目标失败:', e)
      showToast({ title: '行程加载失败', icon: 'none' })
      return
    }
  } else if (!messages.value.length) {
    // 冷启动：没有 tripId 就是一次全新对话，直接给开场白，不再要求先建行程
    messages.value = [getIntroMessage()]
  }
  await restoreSession()
  await restoreSlotState()

  // 首页搜索框 / 探索页带过来的那句话：等登录与历史恢复都做完再自动发出去，
  // 用户从「输入」到「看到 AI 回应」中间不需要任何额外操作。
  const q = wantQuery
  if (q) {
    // 从入口带话进来时视为一次新对话：清掉旧会话与旧槽位，避免把新问题接在旧上下文后面
    sessionId.value = ''
    chatHistory.value = []
    tripStore.setSlotState(null)
    if (!messages.value.some((m) => m.role === 'user')) {
      messages.value = [getIntroMessage()]
    }
    messages.value.push({ role: 'user', content: q })
    await callAiChat(q)
  }
}

/**
 * 首页（tab 模式）每次进入都开一个新对话。
 *
 * 用户切回首页就是想规划新的行程，如果接着上一轮继续聊，
 * 会让人以为"怎么还没清空"，与「首页=开新对话」的心智不符。
 * 带 tripId 的独立对话页不走这条分支（见 initPlanner）。
 */
onMounted(async () => {
  if (!props.tabMode) {
    await initPlanner()
    return
  }
  tripStore.resetForNewTrip()
  selectedTripId.value = ''
  sessionId.value = ''
  chatHistory.value = []
  tripStore.setSlotState(null)
  messages.value = [getIntroMessage()]
  suggestedQuestions.value = []
})

function getIntroMessage(): ChatMessage {
  const t = currentTrip.value
  // 冷启动：用户从首页或对话页直接进来，还没有任何行程信息。
  // 开场白负责交代「我是谁」，紧接着下方会给出三个可选起点（见 ENTRY_OPTIONS），
  // 让用户不用对着空白输入框凭空想。
  if (!t) {
    return {
      role: 'assistant',
      content:
        '我是小笺，你的行程规划助手。\n不用先想清楚 —— 从下面挑一个开始，也可以直接跟我说你想去哪。'
    }
  }
  const city = t.toCity || '目的地'
  // 有 currentTrip 时也不再复述参数：用户自己刚填过，机器人口吻地念一遍
  // （"已收到你的需求：目的地X、Y天…"）是反馈里明确点名的反面样板。
  return {
    role: 'assistant',
    content: `我们在聊「${t.title || `${city}行程`}」这趟安排。\n想改哪儿直接说，也可以继续补充想法 —— 我都记着 ✅`
  }
}

/** 从槽位快照里取一个值（没有则空串），用于冷启动时拼 base_info */
function slotText(key: string): string {
  return tripStore.slotState.slots?.[key]?.value || ''
}

/**
 * 组装智能体入参。
 *
 * 冷启动后这里不再返回 null：没有 currentTrip 时用槽位引擎已收集到的值兜底，
 * 全部字段允许为空 —— 后端 BaseInfo 已全部改为可选，缺什么由 AI 在对话里补。
 */
function buildBaseInfo(): BaseInfoParams {
  const t = currentTrip.value
  const slotDays = Number(slotText('days'))
  return {
    departure_city: t?.fromCity || slotText('departCity') || '',
    destination_city: t?.toCity || slotText('destination') || '',
    start_day: t?.startDate || '',
    end_date: t?.endDate || '',
    days: t?.days || (Number.isFinite(slotDays) && slotDays > 0 ? slotDays : 0),
    hobby: t?.tags || [],
    people_num: t?.people ? String(t.people) : slotText('people'),
    budget: t?.budget ? String(t.budget) : slotText('budget'),
    // 外部带入的行程上下文（如游记/景点），透传给智能体作为首轮提示词
    context_note: t?.contextNote || ''
  }
}

function buildChatRequest(userInput: string): ChatRequestParams {
  return {
    session_id: sessionId.value || undefined,
    user_input: userInput,
    base_info: buildBaseInfo(),
    chat_history: chatHistory.value
  }
}

/** 预算约束：负数预算不允许进入智能规划 */
function validateTripBudget(): boolean {
  const t = currentTrip.value
  if (t && Number(t.budget) < 0) {
    showToast({ title: '预算不能为负数', icon: 'none' })
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
 * 多轮对话：优先走 SSE 流式，不可用时回落到整包接口。
 * @param userInput 本轮用户输入
 */
async function callAiChat(userInput: string) {
  // 预算约束：负数预算不允许进入智能规划
  if (!validateTripBudget()) return

  const params = buildChatRequest(userInput)

  loadingAi.value = true
  aiError.value = ''
  // 清掉上一轮的追问候选：本轮结果回来后会重新下发，留着会让新旧混在一起
  suggestedQuestions.value = []

  const tips = ['正在分析你的需求...', '正在匹配最佳景点...', '正在规划每日行程...', '正在优化游玩路线...']
  let tipIdx = 0
  const tipTimer = setInterval(() => {
    tipIdx = (tipIdx + 1) % tips.length
    loadingText.value = tips[tipIdx]
  }, 2000)

  try {
    // 流式优先：先逐字显示，首字几百毫秒就能看到。
    // 任一环节不可用（小程序端、SSE 建连失败、后端没起流式端点）都回落整包，保证对话永远能用。
    if (canStreamChat() && (await tryStreamChat(params))) {
      return
    }
    const response: AiChatResponse = await chatWithAi(params)
    applyChatResponse(response)
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

/** 把后端响应落到会话状态（流式与非流式共用，避免两套收尾逻辑跑偏） */
function applyChatResponse(response: AiChatResponse) {
  sessionId.value = response.session_id
  // 当前会话只在本地作为活动规划保存；指定行程还要写入行程自身关联字段。
  setActiveChatSession(response.session_id)
  if (currentTrip.value && !currentTrip.value.chatSessionId) {
    currentTrip.value.chatSessionId = response.session_id
  }
  aiChatResp.value = response
  // 更新对话历史（后端返回的是含本轮 user + assistant 的完整历史）
  chatHistory.value = response.chat_history || []
  // 槽位快照：chip 选项、完整度、渐显入口都由它驱动（后端是唯一事实来源）
  tripStore.setSlotState(response.slot_state)
  // 「猜你想问」：后端随本轮回复一起下发的追问候选
  suggestedQuestions.value = response.suggested_questions || []
}

/** 流式渲染中未落地的气泡下标（-1 = 还没有内容到达） */
let streamingBubble = -1

/**
 * 累积流式片段，并截掉模型附加的候选追问块。
 *
 * 候选块（`<followups>…</followups>`）由 prompt 要求模型写在正文之后，它会被渲染成
 * 下方可点的「猜你想问」，**不该出现在气泡正文里**。
 *
 * 为什么在"累积后的整串"上找标记，而不是逐段判断：流式是逐段到达的，
 * 标记本身可能被切成 `<foll` + `owups>` 两段，逐段匹配会漏掉。
 * 在整串上找天然安全，也不需要在中间态保留半截缓冲。
 */
function appendStreamText(acc: string, text: string): string {
  const next = acc + text
  const i = next.toLowerCase().indexOf(FOLLOWUP_MARK_OPEN)
  return i >= 0 ? next.slice(0, i) : next
}

/**
 * 渲染前的最后一道保险：去掉候选追问块。
 *
 * 流式累积时已经截断过一次，但这里必须再来一次，原因是显示的内容来源不止一条路径：
 * 整包接口的 reply、刷新后从历史恢复的消息、以及**改动上线前就已经存在的数据**。
 * 只在流式那一条路径上防，别的路径照样会把标记漏到界面上（实测就漏过）。
 */
function displayText(content: string): string {
  if (!content) return ''
  const i = content.toLowerCase().indexOf(FOLLOWUP_MARK_OPEN)
  return i >= 0 ? content.slice(0, i) : content
}

/**
 * 尝试流式对话。
 *
 * @returns true=流式已完成（调用方不要再走整包）；false=流式不可用，需要回落
 */
async function tryStreamChat(params: ChatRequestParams): Promise<boolean> {
  streamingBubble = -1
  let acc = ''
  let settled = false

  try {
    await chatWithAiStream(params, {
      onDelta: (text) => {
        if (!text) return
        acc = appendStreamText(acc, text)
        if (streamingBubble < 0) {
          // 第一段到达：撤掉「思考中」气泡，改为真正的回复气泡，用户立刻看到字
          loadingAi.value = false
          messages.value.push({ role: 'assistant', content: '' })
          streamingBubble = messages.value.length - 1
        }
        messages.value[streamingBubble].content = acc
        scheduleScroll()
      },
      onDone: (resp) => {
        settled = true
        applyChatResponse(resp)
        if (streamingBubble < 0) {
          // 极端情况：一个 delta 都没收到（例如模型直接返回空）——兜底用 done 里的完整文本
          messages.value.push({ role: 'assistant', content: resp.reply || '（AI 暂无回复）' })
        } else if (!acc.trim() && resp.reply) {
          messages.value[streamingBubble].content = resp.reply
        }
      }
    })
    return settled
  } catch (err) {
    console.warn('[Chat] 流式失败，回落整包接口:', err)
    // 清掉可能已经出现的半截气泡，避免与整包结果重复
    if (streamingBubble >= 0) {
      messages.value.splice(streamingBubble, 1)
      streamingBubble = -1
    }
    return false
  }
}

/** 流式渲染时滚动很频繁，节流一下，避免每个字符都触发一次 scrollIntoView */
let scrollTimer: ReturnType<typeof setTimeout> | null = null
function scheduleScroll() {
  if (scrollTimer) return
  scrollTimer = setTimeout(() => {
    scrollTimer = null
    scrollToBottom()
  }, 120)
}

/** 暂停/停止当前 AI 生成（只中止对话请求，不误伤槽位/地图等并发请求） */
function stopAiCall() {
  abortRequest('chat')
  showToast({ title: '正在停止...', icon: 'none' })
}

function retryAiCall() {
  // 重试：重新发送上一条用户消息
  const lastUserMsg = [...messages.value].reverse().find((m) => m.role === 'user')
  callAiChat(lastUserMsg?.content || '')
}

// ==================== 槽位问答：点一下就能完善行程 ====================

/** 当前待回答的问题（由后端槽位引擎下发） */
const activeQuestion = computed<SlotQuestion | null>(() => tripStore.slotState.nextQuestion || null)
/** 正在提交槽位回答：期间禁用 chips，避免连点造成状态错乱 */
/** 正在提交槽位回答：期间禁用 chips，避免连点造成状态错乱 */
const slotSubmitting = ref(false)
/** 正在定位取当前位置：同样要禁用 chips，否则连点会并发发起定位 */
const locating = ref(false)

/**
 * 「猜你想问」：后端每轮下发的追问候选（纯规则生成，不额外调模型）。
 * 点一条 = 把这句话作为下一轮输入发出去。
 */
const suggestedQuestions = ref<string[]>([])

/**
 * 点「猜你想问」。
 *
 * 与槽位 chips 的本质区别：槽位是**填参数**（不调模型、毫秒级回路），
 * 这里是**发一句话**，走正常对话链路（流式/整包）。
 */
async function onSuggestPick(question: string) {
  if (loadingAi.value || slotSubmitting.value || !ensureLogin()) return
  // 立刻收起候选，避免点完还留在那让人以为没生效
  suggestedQuestions.value = []
  messages.value.push({ role: 'user', content: question })
  scrollToBottom()
  await callAiChat(question)
}

// ==================== 开场三选一：别让用户凭空想 ====================

const ENTRY_SLOT = '__entry__'

/**
 * 开场选项。
 *
 * 用户刚进对话时脑子里往往只有「想出去玩」这一句，硬让他打字容易卡住。
 * 给三个具体起点，点一下就能把对话跑起来；选项值只在组件内部使用，
 * 点选后会翻译成一句完整的话再发给小笺（见 ENTRY_PROMPTS）。
 */
const ENTRY_QUESTION: SlotQuestion = {
  slot: ENTRY_SLOT,
  text: '',
  hint: '选一个开始，也可以直接打字告诉我',
  allowSkip: false,
  options: [
    { label: '我还没想好去哪', value: 'undecided' },
    { label: '我有想去的地方', value: 'decided' },
    { label: '我想按想玩的项目来定', value: 'byActivity' }
  ]
}

/** 回显给用户看的话（用户看到的是自己点的那个标签） */
const ENTRY_ECHO: Record<string, string> = {
  undecided: '我还没想好去哪',
  decided: '我有想去的地方',
  byActivity: '我想按想玩的项目来定'
}

/**
 * 真正发给小笺的话。
 * 模型只看到这句话，看不到用户点的是哪个按钮，所以必须写成完整意图。
 */
const ENTRY_PROMPTS: Record<string, string> = {
  undecided: '我还没想好去哪，帮我推荐几个适合去的地方吧',
  decided: '我心里已经有想去的地方了',
  byActivity: '我想按自己想玩的项目来安排这次行程'
}

/**
 * 是否展示开场三选一。
 * 条件：还没建会话、对话里只有开场白（用户还没说过话）。
 * 说话之后由槽位问题接管，两者不会同时出现。
 */
const showEntryOptions = computed(
  () => !sessionId.value && messages.value.length <= 1 && !finalizing.value
)

/** 当前该展示哪一组选项：开场三选一优先，其次是槽位问题 */
const visibleQuestion = computed<SlotQuestion | null>(() => {
  if (showEntryOptions.value) return ENTRY_QUESTION
  const q = activeQuestion.value
  if (!q) return null
  // 用户已经打字答过这题（本地临时标记，后端快照回来即失效）→ 不再显示，
  // 否则会出现"我说了这个月月底去，下面却还挂着 这周末/下周/下个月/还没定"
  if (tripStore.answeredSlots.includes(q.slot)) return null
  return q
})

/**
 * 这段输入算不算「在回答当前这道题」。
 *
 * 判定刻意保守：多字、且不是「不知道/随便」这类敷衍话，才认为用户是在作答。
 * 认错（把无关闲聊当成作答）只是少问一题，认不出则会让同一题反复出现 ——
 * 后端 SlotEngine.applyText 用的是同一套克制原则：认不出具体值就只消耗问题、不写值。
 */
function looksLikeAnswerInput(text: string): boolean {
  const t = text.trim()
  if (t.length < 2) return false
  return !/^(不(知道|清楚|确定)|没(想好|定)|随便|都行|嗯+|哦+|好的?|行)$/.test(t)
}

/** 用户打字回答当前问题时，立刻把这一排选项收起来（后端随后会给出同一结论） */
function markCurrentQuestionAnswered() {
  const q = activeQuestion.value
  if (q?.slot) tripStore.markSlotAnswered(q.slot)
}

const ready = computed(() => tripStore.slotState.ready)
const completeness = computed(() => tripStore.slotState.completeness || 0)
const answeredCount = computed(() => Object.keys(tripStore.slotState.slots || {}).length)

/**
 * 「生成行程计划」是否已经具备条件。
 *
 * 这个标记只负责给底部常驻大按钮换样式（浅色 → 品牌渐变 + ✨ 图标），
 * 判定与后端槽位就绪标记一致：目的地 + 天数已知。
 */
const canDraw = computed(() => !!sessionId.value && ready.value.frame && !finalizing.value)

/**
 * 浮出工具条的显示条件（与后端 ready 标记一一对应，前端不自己算完整度）。
 *   ready.hotel     = 完整度 ≥ 阈值 且已知住宿偏好
 *   ready.fullRoute = 完整度 ≥ 路线阈值 且已知住宿偏好
 */
const hotelEntryReady = computed(() => !!sessionId.value && ready.value.hotel)
const routeEntryReady = computed(() => !!sessionId.value && ready.value.fullRoute)
const showToolbar = computed(() => hotelEntryReady.value || routeEntryReady.value)

/** 「选酒店」已经选过就把店名带出来，用户一眼知道这步做完了 */
const hotelEntryLabel = computed(() => {
  const picked = tripStore.selectedHotel
  return picked?.name ? `已选：${picked.name}` : '选酒店'
})

/**
 * 进酒店选择页。
 *
 * 城市优先用当前行程的目的地，其次用槽位里的 destination（行程还没生成时只有槽位）；
 * 档次用槽位 hotelStyle，退到问卷里的住宿偏好 —— 两个都可能为空，酒店页会显示「不限」。
 * 带 from=chat：酒店页确认后要 navigateBack 回对话，而不是再 push 一个对话页（否则栈越堆越深）。
 */
function onPickHotel() {
  const city = tripStore.currentTrip?.toCity || slotValue('destination') || ''
  const level = slotValue('hotelStyle') || tripStore.hotelPreference || ''
  const params = [`city=${encodeURIComponent(city)}`, `level=${encodeURIComponent(level)}`, 'from=chat']
  uni.navigateTo({ url: `/pages/plan/hotel?${params.join('&')}` })
}

/**
 * 进完整路线页。
 *
 * 路线是基于「已生成的行程」画的（酒店 → 各站 → 酒店），所以没有行程时先提示生成，
 * 不要跳过去给用户一个空页面。
 */
function onFullRoute() {
  const trip = tripStore.currentTrip
  if (!trip?.dayPlans?.length) {
    showToast({ title: '先生成行程，再排完整路线', icon: 'none' })
    return
  }
  const parts = [trip.id ? `id=${trip.id}` : '', 'day=0'].filter(Boolean)
  uni.navigateTo({ url: `/pages/trip/route?${parts.join('&')}` })
}

/** 读槽位值（没有则空串），与 store 里的 slotValue 等价，模板外调用更方便 */
function slotValue(key: string): string {
  return tripStore.slotState.slots?.[key]?.value ?? ''
}

// 信息刚够生成完整方案时给一次反馈（只在 false→true 的那一次提示，避免反复打扰）
watch(
  () => ready.value.hotel || ready.value.transport || ready.value.fullRoute,
  (now, before) => {
    if (now && !before) {
      showToast({ title: '信息够了，可以生成完整方案了', icon: 'none' })
    }
  }
)

/** 把选项值翻成展示文案，用于在对话里回显用户的点击 */
function optionLabel(slot: string, value: string): string {
  const opt = activeQuestion.value?.options?.find((o) => o.value === value)
  return opt?.label || value
}

/**
 * 出发地槽位「📍 用当前位置」的哨兵值。
 *
 * 必须与 `question-tree.yml` 里 departCity 第一项的 value 完全一致：
 * 后端只负责把这个选项下发下来（定位是端能力，后端拿不到用户的 GPS），
 * 真正解析成城市名由前端完成，再按普通槽位回答提交上去。
 */
const LOCATE_OPTION_VALUE = '__locate__'

function onChipSelect(slot: string, value: string) {
  // 开场三选一走的是「意图 -> 让模型回应」的链路，不是槽位赋值
  if (slot === ENTRY_SLOT) {
    onEntrySelect(value)
    return
  }
  // 「用当前位置」同样是端能力：先定位拿到城市名，再当普通选项提交
  if (value === LOCATE_OPTION_VALUE) {
    return onLocateSelect(slot)
  }
  return answerSlot(slot, value, false, optionLabel(slot, value))
}

/**
 * 处理开场三选一。
 *
 * 为什么不直接当槽位回答：用户点的是「意图」（我还没想好/我有目标），不是参数。
 * 需要小笺先顺着这个意图回应（比如推荐几个目的地），
 * 槽位引擎会在同一轮里照常下发下一个问题（如「你想去哪里？」+ 热门城市），
 * 于是「不知道去哪」的用户也能点着往下走。
 */
async function onEntrySelect(value: string) {
  if (loadingAi.value || !ensureLogin()) return
  const echo = ENTRY_ECHO[value] || value
  const prompt = ENTRY_PROMPTS[value] || echo
  // 先回显用户点的那句话，messages 长度随即 > 1，选项区自动收起
  messages.value.push({ role: 'user', content: echo })
  // 开场选项同理立即收起（正常路径由 messages.length > 1 收起，这里兜住异步竞态）
  tripStore.markSlotAnswered(ENTRY_SLOT)
  scrollToBottom()
  await callAiChat(prompt)
}

function onChipSkip(slot: string) {
  const label = activeQuestion.value?.skipLabel || '跳过'
  return answerSlot(slot, '', true, label)
}

/**
 * 点「📍 用当前位置」：定位 → 逆地理编码 → 用城市名回答出发地。
 *
 * 失败时**不提交**：出发地直接影响车票查询与首日路线，宁可让用户手动点一个城市，
 * 也不要写一个猜的城市进去。失败后问题仍留在屏上，用户可继续点别的城市或「暂不确定」。
 *
 * 最常见的失败原因是环境：线上跑的是 http，浏览器会直接拒绝定位请求
 * （只有 https / localhost 才被允许），App 内不受此限制。
 */
async function onLocateSelect(slot: string) {
  if (loadingAi.value || slotSubmitting.value || locating.value || !ensureLogin()) return
  locating.value = true
  showToast({ title: '正在定位…', icon: 'none' })
  try {
    const city = await locateCurrentCity()
    if (!city) {
      showToast({ title: '定位失败，请手动选择出发城市', icon: 'none' })
      return
    }
    await answerSlot(slot, city, false, `当前位置：${city}`)
  } finally {
    locating.value = false
  }
}

/**
 * 提交一次槽位回答。
 *
 * 这条链路刻意不走大模型：点一下选项就该在百毫秒内出下一个问题。
 * 只有自由文本输入才走 /travel/chat → LLM。
 */
async function answerSlot(slot: string, value: string, skipped: boolean, echo: string) {
  if (!sessionId.value || slotSubmitting.value) return
  slotSubmitting.value = true
  // 上一轮的「猜你想问」到这里就过期了：它是按上一轮 AI 回复生成的，用户已经点了新选项，
  // 还留在屏幕上就会出现「已经选了成都、却还挂着大理/厦门」—— 实测反馈的就是这个。
  //
  // 清掉之后屏上只剩一个槽位问题，这正是「问一轮、答一轮」的预期：
  // 槽位链路（/travel/plan/slot）是纯规则的，本身不下发候选追问，
  // 下一次走 /travel/chat 时才会按新状态重新给。
  suggestedQuestions.value = []
  // 立即回显用户的选择，避免"点了没反应"
  messages.value.push({ role: 'user', content: echo })
  scrollToBottom()
  try {
    const next = await submitSlotAnswer({ sessionId: sessionId.value, slot, value, skipped })
    tripStore.setSlotState(next)
    const q = next.nextQuestion
    const reply = q ? q.text : '基本信息够了，点下方按钮就能生成行程。'
    messages.value.push({ role: 'assistant', content: reply })

    // [F] 把这一轮「点选项」的问答补进对话历史，让模型看得见用户点过什么。
    //
    // 为什么必须补：槽位链路（/travel/plan/slot）纯规则、不调模型也不落库，
    // 模型那边只能从 base_info.slot_note 的一行字里知道"目的地=成都"，
    // 既看不到用户点了什么、也看不到系统问了什么，上下文天然滞后一轮 ——
    // 于是它下一轮的回复与候选追问会按更早的语境走
    // （实测现象：已经选了成都，还在继续聊第一轮提过的大理/厦门）。
    // 补进去之后，下一轮 /travel/chat 会带上这两条一起提交，模型看到的就和用户屏幕一致了。
    //
    // 只补内存里的 chatHistory、**不落库**：chat_history 表仍只存模型回合，
    // 后端统计轮次（round）用的也是模型回合数，语义不变。
    chatHistory.value.push({ role: 'user', content: echo })
    chatHistory.value.push({ role: 'assistant', content: reply })

    scrollToBottom()
  } catch (e) {
    console.warn('槽位提交失败:', e)
    showToast({ title: '提交失败，请重试', icon: 'none' })
  } finally {
    slotSubmitting.value = false
  }
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
    showToast({ title: 'AI 正在生成中，请稍候', icon: 'none' })
    return
  }
  if (!ensureLogin()) return
  const text = inputText.value.trim()
  if (!text) return
  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  // 打字作答也要立刻收起这一排选项：不能等后端回复，否则中间那几秒
  // 屏幕上仍是"用户已经答了、选项还挂着"的矛盾状态（后端随后会给出同一结论）
  if (looksLikeAnswerInput(text)) {
    markCurrentQuestionAnswered()
  }
  scrollToBottom()

  // 真实多轮调用：带上本轮 user_input，后端会把历史上下文一并提交给智能体
  await callAiChat(text)
}

/** 登录拦截：对话/生成链路需要登录，未登录先去登录页（带 redirect 回跳） */
function ensureLogin(): boolean {
  if (isLoggedIn()) return true
  showToast({ title: '请先登录', icon: 'none' })
  setTimeout(() => redirectToLogin(), 400)
  return false
}

/**
 * 「使用 AI 记住的偏好」开关已按产品要求停用（UI 与调用一并移除）。
 *
 * 停用范围：不再把历史画像回灌给模型 —— 由后端 sky.profile.inject-enabled 控制，默认 false。
 * 后台仍会继续沉淀画像数据（无副作用），将来要恢复时：
 *   1) 后端把 sky.profile.inject-enabled 设为 true
 *   2) 前端从 git 历史里取回 loadProfileSwitch / toggleProfileSwitch 与 .memory-row 那段模板
 * 之所以不在这里留一段 `v-if="false"` 的死代码：开关本来就是"先停用"，不是"留个位置"。
 */

function goBack() {
  uni.navigateBack()
}

/**
 * 「生成行程计划」——底部常驻大按钮的点击处理。
 *
 * 取代了原来的「保存草稿」—— 那是个假按钮：点了只弹一句"草稿已保存"，
 * 既没有存草稿接口，也没有任何草稿实体，属于纯粹的误导。
 *
 * 现在的语义是「把行程画出来」，而且**落点与生成完成后的「查看详情 →」完全一致**：
 *   · 已经生成过行程 → 直接进那趟行程的详情页
 *   · 生成过但没入库   → 进详情页，用内存里的行程直接渲染
 *   · 只是聊过还没生成 → 先触发生成，出结果后自动进详情页
 *   · 连一句都没聊     → 提示先和小笺说一句
 *
 * 为什么不再跳「今日路线」：那条路只画单日闭环，用户点「生成行程计划」想看的是
 * 整趟行程（每天安排 + 地图），而详情页里本来就有「地图导航」二级入口进今日路线。
 * 落点统一成详情页之后，无论从按钮还是从预览卡片进，看到的都是同一个页面。
 */
async function onDrawRoute() {
  const savedId = currentTrip.value?.id
  if (savedId) {
    goPlanDetail(savedId)
    return
  }
  if (aiPlanData.value) {
    goPlanDetail('')
    return
  }
  if (!sessionId.value) {
    showToast({ title: '先跟小笺聊一句，我才能画路线', icon: 'none' })
    return
  }
  // 先生成，生成完直接进详情页（与预览卡片「查看详情 →」同一落点）
  await goItinerary()
  const id = currentTrip.value?.id
  if (id || aiPlanData.value) {
    goPlanDetail(id || '')
  }
}

/** 精细设置：进旧的表单式规划页（wizard），供想自己填具体日期的用户使用 */
function goWizard() {
  uni.navigateTo({ url: '/pages/plan/wizard' })
}

/**
 * 点击"生成行程计划"：提交异步任务 → 轮询阶段进度（检索/框架/细化）→
 * 完成前实时展示骨架预览与已用时，支持中途取消；
 * 同一行程命中缓存时直接秒开。
 */
async function goItinerary() {
  if (!ensureLogin()) return
  if (!sessionId.value) {
    showToast({ title: '请先发送消息与AI对话', icon: 'none' })
    return
  }
  if (finalizing.value) return
  // 预算约束：负数预算不允许进入智能规划
  if (!validateTripBudget()) return

  const params = buildChatRequest('请综合以上所有讨论内容，生成最终的完整旅游行程计划')

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
      showToast({ title: '命中缓存，已为你秒开 ⚡', icon: 'none' })
    }

    // detail 完成：用精确 location 重新地理编码，把遮罩内地图更新为精确路线
    drawDetailDayOnMap(finalPlan, genMapDayIndex.value)

    // 将最终 AI 响应映射为 TripPlan 并存储。
    // 冷启动时没有 currentTrip，目的地/出发地要从槽位快照里取，
    // 否则生成出来的行程标题和城市都是空的。
    const tripParams = {
      fromCity: currentTrip.value?.fromCity || slotText('departCity'),
      toCity: currentTrip.value?.toCity || slotText('destination'),
      tags: currentTrip.value?.tags || []
    }
    const mappedTrip = mapPlanResponseToTripPlan(finalPlan, tripParams)
    // 将会话和最终行程绑定，之后只能从该行程入口恢复对应对话。
    mappedTrip.chatSessionId = sessionId.value
    // 保留原始预算和人数信息。
    // mapPlanResponseToTripPlan 内部固定返回 budget=0 / people=1，
    // 这里必须回填真实值，否则预算环形图会显示 0、人数永远是 1。
    const slotBudget = Number(slotText('budget'))
    const slotPeople = Number(slotText('people'))
    mappedTrip.budget =
      currentTrip.value?.budget || (Number.isFinite(slotBudget) && slotBudget > 0 ? slotBudget : 0)
    mappedTrip.people =
      currentTrip.value?.people || (Number.isFinite(slotPeople) && slotPeople > 0 ? slotPeople : 1)
    mappedTrip.startDate = currentTrip.value?.startDate || ''
    mappedTrip.endDate = currentTrip.value?.endDate || ''
    mappedTrip.fromCity = tripParams.fromCity
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
      showToast({ title: '行程已生成，但保存失败，请稍后在详情页重试保存', icon: 'none', duration: 2500 })
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
      showToast({ title: '已取消生成，可随时重新生成', icon: 'none', duration: 2000 })
    }
    // 超时（agent 生成慢）给友好提示，避免显示原始错误串
    else if (msg.includes('timeout') || msg.includes('超时')) {
      showToast({ title: '生成超时，AI 服务繁忙，请稍后重试', icon: 'none', duration: 3000 })
    } else {
      showToast({ title: err?.data?.msg || msg || '生成最终行程失败', icon: 'none' })
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

/** 进行程详情页（含地图路线）：生成结果预览与底部大按钮共用同一落点 */
function goPlanDetail(tripId: string | number) {
  // 没拿到 id 时退回 currentTrip：详情页无 id 直接用内存里的行程渲染（见 trip/detail onMounted）
  const id = tripId || currentTrip.value?.id || ''
  uni.navigateTo({ url: id ? `/pages/trip/detail?id=${id}` : '/pages/trip/detail' })
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

/*
 * tab 模式（首页）的底部避让。
 *
 * 自定义 TabBar 是 fixed 且高约 128rpx + 16rpx 安全间距，输入区如果直接贴底会被压住，
 * 用户就点不到输入框了。这里整体抬起一个 TabBar 的高度。
 */
.footer.tab-mode {
  bottom: calc(160rpx + env(safe-area-inset-bottom));
  padding-bottom: 24rpx;
}

/* 输入区上移后，滚动区要相应多留底部空白，否则最后一条消息会被输入区挡住 */
.page.as-tab .chat-scroll {
  padding-bottom: 300rpx;
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

.nav-text-btn {
  font-size: var(--fs-body);
  color: $mint-primary;
  font-weight: 500;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

/* 次要入口：不用主色，避免和底部的「生成行程计划」抢注意力 */
.nav-text-btn.plain {
  color: var(--text-tertiary);
  font-weight: 400;
}

/*
 * 底部常驻「生成行程计划」。
 * 默认是"还没聊够"的浅色态，信息够了（canDraw）才切成品牌渐变 ——
 * 与原来顶栏那枚按钮的 ready 反馈一致，只是位置换到了设计稿指定的输入区下方。
 */
.gen-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  height: 96rpx;
  border-radius: 999rpx;
  background: var(--bg-input);
  border: 1rpx solid var(--border);
  transition: all 0.2s ease;

  &.ready {
    background: var(--brand-grad);
    border-color: transparent;
    box-shadow: var(--brand-glow);
  }

  &:active {
    transform: scale(0.98);
  }
}

/* 浮出工具条：完整度够了才出现，给「选酒店 / 生成完整路线」两个下一步入口 */
.chat-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  padding: 0 0 16rpx;
  animation: toolbar-in 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes toolbar-in {
  from {
    opacity: 0;
    transform: translateY(16rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.tool-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
  max-width: 100%;
  padding: 14rpx 28rpx;
  border-radius: 999rpx;
  background: var(--bg-card);
  border: 1rpx solid var(--border);
  box-shadow: var(--shadow-sm);
  font-size: var(--fs-meta);
  font-weight: 600;
  color: var(--text-main);

  &.primary {
    background: var(--brand-grad);
    border-color: transparent;
    color: var(--on-brand);
    box-shadow: var(--brand-glow);
  }

  &:active {
    transform: scale(0.97);
  }
}

.gen-btn-text {
  font-size: var(--fs-title);
  font-weight: 700;
  color: var(--text-tertiary);
}

.gen-btn.ready .gen-btn-text {
  color: var(--on-brand);
}

/* Mock 模式告警条：黄底，放在消息流最上方，一眼可见 */
.mock-bar {
  padding: 16rpx 24rpx;
  margin-bottom: 20rpx;
  border-radius: 16rpx;
  background: #fff4d6;
  border: 1rpx solid #f0c97a;
}

.mock-text {
  font-size: var(--fs-meta);
  color: #8a6100;
  line-height: 1.5;
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

/* 需求完整度进度条：只在有进展时占位，避免空进度条带来「又要填一堆」的错觉 */
.slot-bar {
  padding-bottom: 12rpx;
  border-bottom: 1rpx solid var(--border);
  margin-bottom: 12rpx;
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

/* 「生成行程计划」按钮的样式见上方 .gen-btn（底部输入区下方）。
   这里只留生成进度遮罩。 */

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

/* 生成进度遮罩退场动画：仅 leave 分支，入场仍由 .gen-card 自身动画负责 */
.fb-fade-leave-active {
  transition: opacity 0.18s ease;
}

.fb-fade-leave-to {
  opacity: 0;
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
