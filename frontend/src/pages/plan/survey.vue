<template>
  <view class="page">
    <view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="nav-inner" :style="{ height: navHeight + 'px' }">
        <view class="back" @tap="goBack">
          <AppIcon name="chevron-left" :size="34" color="var(--text-body)" />
        </view>
        <text class="nav-title">新建行程</text>
      </view>
    </view>

    <scroll-view
      scroll-y
      class="body"
      :style="{ paddingTop: navHeight + 'px' }"
    >
      <!-- 步骤指示器 + 进度条（设计稿位置：标题下方、表单上方） -->
      <view class="step-head">
        <text class="step-indicator">{{ step }} / {{ TOTAL_STEPS }} {{ stepLabels[step - 1] }}</text>
        <view class="step-track">
          <view class="step-fill" :style="{ width: stepPercent + '%' }" />
        </view>
      </view>

      <text class="ai-hint">小笺正在确认您的旅行需求</text>

      <!-- ============ 1/3 基础信息 ============ -->
      <view v-if="step === 1" class="step-body">
        <view class="field">
          <text class="field-label">目的地</text>
          <picker mode="selector" :range="CITY_OPTIONS" :value="cityIndex" @change="onCityChange">
            <view class="pick">{{ destination }}</view>
          </picker>
        </view>

        <view class="field">
          <text class="field-label">出行天数</text>
          <picker mode="selector" :range="DAY_OPTIONS_TEXT" :value="daysIndex" @change="onDaysChange">
            <view class="pick">{{ days }}</view>
          </picker>
        </view>

        <view class="field">
          <text class="field-label">出行日期</text>
          <picker mode="date" :value="startDate" @change="onDateChange">
            <view class="pick">{{ startDate }}</view>
          </picker>
        </view>
      </view>

      <!-- ============ 2/3 偏好设置 ============ -->
      <view v-else-if="step === 2" class="step-body">
        <view
          v-for="q in questions"
          :key="q.id"
          class="question-block"
        >
          <text class="question-text">
            {{ q.text }}{{ q.emoji ? ` ${q.emoji}` : '' }}
          </text>
          <view class="options">
            <view
              v-for="opt in q.options"
              :key="opt"
              class="option"
              :class="{ selected: answers[q.id] === opt }"
              @tap="selectOption(q.id, opt)"
            >{{ opt }}</view>
          </view>
        </view>

        <view class="add-custom" @tap="openCustomInput">
          <text>手动添加需求</text>
          <text class="plus">+</text>
        </view>

        <view v-if="customNeeds.length" class="custom-list">
          <view
            v-for="(item, i) in customNeeds"
            :key="i"
            class="custom-tag"
          >
            <text>{{ item }}</text>
            <text class="remove" @tap="removeCustom(i)">×</text>
          </view>
        </view>
      </view>

      <!-- ============ 3/3 住宿偏好 ============ -->
      <view v-else class="step-body">
        <text class="question-text">住宿想住哪种？🏨</text>
        <text class="question-hint">选一个档次，下一步按它筛酒店；也可以选「无要求」自己挑。</text>
        <view class="options">
          <view
            v-for="level in HOTEL_LEVEL_CHOICES"
            :key="level"
            class="option"
            :class="{ selected: hotelPreference === level }"
            @tap="hotelPreference = level"
          >{{ level }}</view>
        </view>
      </view>

      <view style="height: 260rpx" />
    </scroll-view>

    <!-- 底部按钮：上一步（第 1 步隐藏）+ 下一步/选酒店，位置与设计稿一致 -->
    <view class="footer safe-bottom">
      <button class="btn-prev" :class="{ hidden: step === 1 }" @tap="prevStep">上一步</button>
      <button class="btn-next" @tap="nextStep">
        {{ step === TOTAL_STEPS ? '选择酒店' : '下一步' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 问卷页（新建行程的第 1 步）。
 *
 * 跳转链路（与设计稿一致）：
 *   首页悬浮「+」 → 本页 → 选择酒店页（pages/plan/hotel） → AI 对话页（pages/ai/chat）
 *
 * 改造点：
 *   · 原来是一整页平铺所有问题 + 单个「提交需求」按钮，现在按设计稿拆成 3 步，
 *     底部固定「上一步 / 下一步」，降低一次性填完的心理负担。
 *   · 目的地/天数/出行日期改成可选（原来只能靠外部页面带 query 进来，
 *     而首页悬浮按钮进来是没有参数的，默认会变成「重庆 3 天」）。
 *   · 新增「住宿偏好」，它是酒店选择页的默认筛选条件。
 *
 * 未改动：槽位引擎、AI 对话、接口调用 —— 本页只负责组装 currentTrip 并把用户交给下一步。
 */
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon/AppIcon.vue'
import { useTripStore } from '@/store/trip'
import {
  buildSurveyQuestions,
  budgetToAmount,
  paceToTag
} from '@/utils/surveyQuestions'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'
import { showModal, showToast } from '@/utils/feedback'

const tripStore = useTripStore()

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const navHeight = statusBarHeight + 48

const TOTAL_STEPS = 3
const stepLabels = ['基础信息', '偏好设置', '住宿偏好']
const step = ref(1)
const stepPercent = computed(() => Math.round((step.value / TOTAL_STEPS) * 100))

/** 目的地候选（与设计稿问卷下拉一致，另补上对话里常见的几个城市） */
const CITY_OPTIONS = ['肇庆', '广州', '桂林', '深圳', '厦门', '成都', '重庆', '杭州']
const DAY_OPTIONS = [1, 2, 3, 4, 5]
const DAY_OPTIONS_TEXT = DAY_OPTIONS.map((d) => `${d}天`)

/** 住宿档次 + 「无要求」：与 hotels.ts 的 HotelLevel 对应 */
const HOTEL_LEVEL_CHOICES = ['经济型', '舒适型', '高档型', '特色民宿', '无要求']

const destination = ref(CITY_OPTIONS[0])
const cityIndex = ref(0)
const days = ref('1天')
const daysIndex = ref(0)
const startDate = ref(formatDate(new Date()))

const answers = reactive<Record<string, string>>({})
const customNeeds = ref<string[]>([])
/** 住宿偏好：默认「无要求」，避免用户没想法时被卡在第 3 步 */
const hotelPreference = ref('无要求')

const questions = computed(() => buildSurveyQuestions(destination.value, days.value))

onLoad((query) => {
  if (query?.city) {
    const city = decodeURIComponent(String(query.city))
    destination.value = city
    const i = CITY_OPTIONS.indexOf(city)
    // 外部带进来的城市不在候选里时，临时插到第一位，别把它悄悄换成肇庆
    if (i >= 0) {
      cityIndex.value = i
    } else {
      CITY_OPTIONS.unshift(city)
      cityIndex.value = 0
    }
  }
  if (query?.days) {
    const d = parseInt(decodeURIComponent(String(query.days)), 10)
    if (Number.isFinite(d) && d > 0) {
      const clamped = Math.min(Math.max(d, DAY_OPTIONS[0]), DAY_OPTIONS[DAY_OPTIONS.length - 1])
      days.value = `${clamped}天`
      daysIndex.value = DAY_OPTIONS.indexOf(clamped)
    }
  }
})

function formatDate(d: Date): string {
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

function onCityChange(e: { detail: { value: string | number } }) {
  cityIndex.value = Number(e.detail.value) || 0
  destination.value = CITY_OPTIONS[cityIndex.value]
}

function onDaysChange(e: { detail: { value: string | number } }) {
  daysIndex.value = Number(e.detail.value) || 0
  days.value = DAY_OPTIONS_TEXT[daysIndex.value]
}

function onDateChange(e: { detail: { value: string | number } }) {
  startDate.value = String(e.detail.value)
}

function selectOption(questionId: string, option: string) {
  answers[questionId] = option
}

function openCustomInput() {
  showModal({
    title: '手动添加需求',
    editable: true,
    placeholderText: '如：想住江景房、避开网红店排队等',
    success(res) {
      if (!res.confirm) return
      const text = (res.content || '').trim()
      if (!text) return
      if (!customNeeds.value.includes(text)) {
        customNeeds.value.push(text)
      }
    }
  })
}

function removeCustom(index: number) {
  customNeeds.value.splice(index, 1)
}

function goBack() {
  uni.navigateBack({
    fail: () => uni.switchTab({ url: '/pages/home/index' })
  })
}

function prevStep() {
  if (step.value > 1) step.value--
}

function nextStep() {
  if (step.value === 1) {
    if (!days.value) {
      showToast({ title: '请选择出行天数', icon: 'none' })
      return
    }
    step.value = 2
    return
  }
  if (step.value === 2) {
    const missing = questions.value.find((q) => !answers[q.id])
    if (missing) {
      showToast({ title: '请完成所有问题', icon: 'none' })
      return
    }
    step.value = 3
    return
  }
  submitRequirements()
}

/**
 * 组装 currentTrip 并进入酒店选择页。
 *
 * 注意 resetForNewTrip 会清掉住宿偏好，所以 hotelPreference / currentTrip 的写入都放在它之后。
 */
function submitRequirements() {
  // 前置登录校验：这条链路后面是「选酒店 → 对话生成」，两处都要登录态。
  // 原来没有校验，未登录用户能填完 3 步问卷 + 选完酒店，直到对话页才被弹去登录 ——
  // 前面的输入时间白费，酒店页还会因 401 反复跳登录。
  if (!isLoggedIn()) {
    showToast({ title: '先登录才能保存你的规划', icon: 'none' })
    setTimeout(() => redirectToLogin(), 300)
    return
  }

  const tags = [
    paceToTag(answers.pace),
    answers.interest,
    answers.companion,
    ...customNeeds.value
  ]

  tripStore.resetForNewTrip()
  tripStore.setHotelPreference(hotelPreference.value)

  const dayCount = parseInt(days.value, 10) || 1
  tripStore.currentTrip = {
    title: `${destination.value}${dayCount}日游`,
    // 出发地留空，交给对话链路的槽位（departCity）去收集 ——
    // 原来写死 '当前城市'，会被当成真实城市透传给 /travel/chat
    // （后端没有对这个字符串做任何特判，大交通/首日路线都会拿到这四个字）。
    fromCity: '',
    toCity: destination.value,
    days: dayCount,
    startDate: startDate.value,
    endDate: addDays(startDate.value, dayCount - 1),
    budget: budgetToAmount(answers.budget),
    people: answers.companion.includes('独自') ? 1
      : answers.companion.includes('双人') ? 2
      : answers.companion.includes('亲子') ? 3
      : 4,
    tags,
    dayPlans: []
  }

  uni.navigateTo({
    url: `/pages/plan/hotel?city=${encodeURIComponent(destination.value)}`
      + `&level=${encodeURIComponent(hotelPreference.value)}`
  })
}

/** 起始日 + n 天 → 'YYYY-MM-DD'（endDate 含当天，所以传 days-1） */
function addDays(start: string, offset: number): string {
  const d = new Date(`${start}T00:00:00`)
  if (Number.isNaN(d.getTime())) return start
  d.setDate(d.getDate() + offset)
  return formatDate(d)
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: linear-gradient(180deg, var(--survey-top) 0%, var(--bg-page) 40%, var(--bg-page) 100%);
}

.nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: transparent;
}

.nav-inner {
  display: flex;
  align-items: center;
  padding: 0 32rpx;
}

.back {
  display: flex;
  align-items: center;
  margin-right: 16rpx;
  padding: 8rpx;
}

.nav-title {
  font-size: var(--fs-subhead);
  font-weight: 700;
  color: var(--text-main);
}

.body {
  height: 100vh;
  padding: 0 32rpx;
  box-sizing: border-box;
}

/* ── 步骤指示器 ── */
.step-head {
  padding: 24rpx 0 20rpx;
}

.step-indicator {
  display: block;
  font-size: var(--fs-meta);
  font-weight: 600;
  color: var(--primary-strong);
  margin-bottom: 16rpx;
}

.step-track {
  width: 100%;
  height: 12rpx;
  border-radius: 999rpx;
  background: var(--bg-input);
  overflow: hidden;
}

.step-fill {
  height: 100%;
  border-radius: 999rpx;
  background: var(--brand-grad);
  transition: width 0.3s ease;
}

.ai-hint {
  display: block;
  text-align: center;
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
  margin-bottom: 32rpx;
}

.step-body {
  padding-bottom: 24rpx;
}

/* ── 表单 ── */
.field {
  margin-bottom: 32rpx;
}

.field-label {
  display: block;
  font-size: var(--fs-meta);
  font-weight: 700;
  color: var(--text-main);
  margin-bottom: 16rpx;
}

.pick {
  height: 88rpx;
  line-height: 88rpx;
  padding: 0 32rpx;
  background: var(--bg-card);
  border: 1rpx solid var(--border);
  border-radius: 24rpx;
  font-size: var(--fs-body);
  color: var(--text-main);
}

.question-block {
  margin-bottom: 40rpx;
}

.question-text {
  font-size: var(--fs-title);
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.5;
  display: block;
  margin-bottom: 24rpx;
}

.question-hint {
  display: block;
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
  line-height: 1.6;
  margin: -12rpx 0 24rpx;
}

.options {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.option {
  padding: 28rpx 32rpx;
  background: var(--bg-muted);
  border-radius: 16rpx;
  font-size: var(--fs-body);
  color: var(--text-main);
  line-height: 1.4;
  border: 2rpx solid transparent;
  transition: all 0.2s;

  &.selected {
    background: var(--brand-soft);
    border-color: var(--brand);
    color: var(--brand-deep);
    font-weight: 600;
  }
}

.add-custom {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  padding: 20rpx;
  background: var(--bg-card);
  border-radius: 16rpx;
  font-size: var(--fs-body);
  color: var(--text-secondary);
  border: 1rpx dashed var(--border-strong);
}

.plus {
  font-size: var(--fs-title);
  color: var(--brand-ink);
}

.custom-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 24rpx;
}

.custom-tag {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 20rpx;
  background: var(--bg-card);
  border: 1rpx solid var(--brand);
  border-radius: 999rpx;
  font-size: var(--fs-meta);
  color: var(--brand-deep);
}

.remove {
  font-size: var(--fs-body);
  color: var(--text-tertiary);
  padding-left: 4rpx;
}

/* ── 底部按钮（设计稿：上一步 flex-1 描边，下一步 flex-2 渐变）── */
.footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 24rpx;
  padding: 20rpx 32rpx calc(20rpx + env(safe-area-inset-bottom));
  background: var(--bg-card);
  border-top: 1rpx solid var(--border);
}

.btn-prev,
.btn-next {
  height: 96rpx;
  line-height: 96rpx;
  border-radius: 48rpx;
  font-size: var(--fs-title);
  border: none;

  &::after {
    border: none;
  }
}

.btn-prev {
  flex: 1;
  background: var(--bg-card);
  border: 1rpx solid var(--border-strong);
  color: var(--text-main);

  &.hidden {
    visibility: hidden;
  }
}

.btn-next {
  flex: 2;
  background: var(--brand-grad);
  color: var(--on-brand);
  font-weight: 700;
}
</style>
