<template>
  <view class="page">
    <!-- 顶栏 -->
    <view class="nav" :style="{ paddingTop: statusBarHeight + 'px', height: navHeight + 'px' }">
      <text class="back" @tap="goBack">‹</text>
      <view class="nav-btn" @tap="createEmpty">创建空计划</view>
    </view>

    <scroll-view scroll-y class="body" :style="{ paddingTop: navHeight + 'px' }">
      <!-- 目的地 -->
      <view class="block">
        <text class="block-title">你想去哪里？</text>
        <view class="input-box" @tap="openCityPicker">
          <view v-if="destination" class="tags">
            <view class="city-tag">
              <text>{{ destination }}</text>
              <text class="remove" @tap.stop="destination = ''">×</text>
            </view>
          </view>
          <view class="input-row">
            <text class="ai-icon">✦</text>
            <input
              v-model="destinationInput"
              class="input"
              placeholder="搜索或选择城市"
              placeholder-class="placeholder"
              @focus="openCityPicker"
              @confirm="addCityFromInput"
            />
            <text v-if="!destination" class="pick-hint">去选城市 ›</text>
          </view>
        </view>
      </view>

      <!-- 天数 -->
      <view class="block">
        <text class="block-title">你想去多久？</text>
        <view class="picker-row" @tap="openCalendar">
          <text class="picker-icon">📅</text>
          <view class="duration-info">
            <text class="picker-value">{{ form.days }}天</text>
            <text v-if="dateRangeText" class="date-range">{{ dateRangeText }}</text>
          </view>
          <text class="picker-arrow">›</text>
        </view>
      </view>

      <!-- 旅行偏好 -->
      <view class="block">
        <text class="block-title">旅行偏好</text>
        <view class="pref-grid">
          <view
            v-for="pref in TRAVEL_PREFERENCES"
            :key="pref.id"
            class="pref-chip"
            :class="{ active: selectedPrefs.includes(pref.id) }"
            @tap="togglePref(pref.id)"
          >
            <text>{{ pref.emoji }} {{ pref.label }}</text>
          </view>
        </view>
      </view>

      <!-- 人数与预算（折叠区域） -->
      <view class="block extra">
        <view class="extra-row">
          <text class="extra-label">人数</text>
          <picker :range="peopleLabels" @change="onPeopleChange">
            <text class="extra-value">{{ form.people }}人 ›</text>
          </picker>
        </view>
        <view class="extra-row">
          <text class="extra-label">预算</text>
          <input
            :value="String(form.budget)"
            class="extra-input"
            type="number"
            placeholder="元"
            @input="onBudgetInput"
          />
        </view>
        <view class="extra-row">
          <text class="extra-label">出发地</text>
          <input v-model="form.fromCity" class="extra-input" placeholder="选填" />
        </view>
      </view>

      <view style="height: 180rpx" />
    </scroll-view>

    <!-- 底部按钮 -->
    <view class="footer safe-bottom">
      <button class="btn-black" :class="{ ready: canGoNext }" @tap="goChat">
        <text>智能规划</text>
      </button>
    </view>

    <view v-if="showCalendar" class="calendar-mask" @tap="closeCalendar">
      <view class="calendar-panel safe-bottom" @tap.stop>
        <view class="calendar-handle" />
        <view class="calendar-head">
          <view>
            <text class="calendar-title">你想去多久？</text>
            <text class="calendar-timezone">时区：Asia/Shanghai</text>
          </view>
          <view class="flex-toggle" :class="{ active: isFlexibleDays }" @tap="toggleFlexibleDays">
            <view class="toggle-dot" />
            <text>灵活天数</text>
          </view>
        </view>
        <scroll-view scroll-y class="calendar-scroll">
          <view v-for="month in calendarMonths" :key="month.key" class="month-block">
            <text class="month-title">{{ month.title }}</text>
            <view class="weekday-grid">
              <text v-for="w in weekdays" :key="w" class="weekday">{{ w }}</text>
            </view>
            <view class="date-grid">
              <view v-for="blank in month.firstDay" :key="'b-' + month.key + '-' + blank" />
              <view
                v-for="day in month.days"
                :key="day.value"
                class="date-cell"
                :class="{
                  selected: isSelectedDate(day.value),
                  inRange: isInRange(day.value),
                  disabled: day.disabled
                }"
                @tap="selectDate(day.value)"
              >
                <text>{{ day.day }}</text>
              </view>
            </view>
          </view>
        </scroll-view>
        <button class="calendar-confirm" @tap="confirmCalendar">
          <text>确定</text>
        </button>
      </view>
    </view>

    <!-- 城市选择面板：搜索 + 热门 + 省份/城市两级 -->
    <view v-if="showCityPicker" class="city-mask" @tap="closeCityPicker">
      <view class="city-panel safe-bottom" @tap.stop>
        <view class="city-handle" />
        <view class="city-head">
          <text class="city-title">选择目的地城市</text>
          <text class="city-close" @tap="closeCityPicker">✕</text>
        </view>
        <!-- 搜索框 -->
        <view class="city-search">
          <text class="search-icon">🔍</text>
          <input
            v-model="cityKeyword"
            class="city-search-input"
            placeholder="搜索城市或省份"
            placeholder-class="placeholder"
            @input="onCitySearch"
            focus
          />
          <text v-if="cityKeyword" class="search-clear" @tap="cityKeyword = ''; cityResults = []">✕</text>
        </view>
        <!-- 搜索结果（有搜索词时展示） -->
        <scroll-view v-if="cityKeyword.trim()" scroll-y class="city-search-results">
          <view v-for="r in cityResults" :key="r.city" class="city-result-item" @tap="pickCity(r.city)">
            <text class="result-city">{{ r.city }}</text>
            <text class="result-prov">{{ r.province }}</text>
          </view>
          <view v-if="!cityResults.length" class="city-empty">未找到相关城市，可手动输入后回车添加</view>
        </scroll-view>
        <template v-else>
          <!-- 热门城市 -->
          <view class="hot-section">
            <text class="hot-label">热门城市</text>
            <view class="hot-grid">
              <view v-for="c in HOT_CITIES" :key="c" class="hot-chip" :class="{ picked: destination === c }" @tap="pickCity(c)">
                <text>{{ c }}</text>
              </view>
            </view>
          </view>
          <!-- 省份/城市两级 -->
          <view class="prov-city">
            <scroll-view scroll-y class="prov-list">
              <view v-for="p in PROVINCE_CITIES" :key="p.name" class="prov-item" :class="{ active: activeProvince === p.name }" @tap="selectProvince(p.name)">
                <text>{{ p.name }}</text>
              </view>
            </scroll-view>
            <scroll-view scroll-y class="city-list">
              <view v-for="c in activeProvinceCities" :key="c" class="city-item" :class="{ picked: destination === c }" @tap="pickCity(c)">
                <text>{{ c }}</text>
              </view>
            </scroll-view>
          </view>
        </template>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { TRAVEL_PREFERENCES } from '@/utils/constant'
import { PROVINCE_CITIES, HOT_CITIES, searchCities } from '@/utils/cityData'
import { useTripStore } from '@/store/trip'
import { useLogin } from '@/hooks/useLogin'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'
import type { GenerateTripParams } from '@/api/trip'

const tripStore = useTripStore()
const { checkLogin } = useLogin()

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
// 导航栏固定高度（px，不随 rpx 缩放），避免 H5/大屏下内容被遮挡
const navHeight = statusBarHeight + 44

const destinationInput = ref('')
const destination = ref('')
const selectedPrefs = ref<string[]>(['classic', 'food'])
const showCalendar = ref(false)
const isFlexibleDays = ref(false)
const tempStartDate = ref('')
const tempEndDate = ref('')

// —— 城市选择面板状态 ——
const showCityPicker = ref(false)
const cityKeyword = ref('')
const activeProvince = ref('广东')
const cityResults = ref<{ city: string; province: string }[]>([])
const activeProvinceCities = computed(() => {
  const p = PROVINCE_CITIES.find((x) => x.name === activeProvince.value)
  return p ? p.cities : []
})

const peopleLabels = Array.from({ length: 10 }, (_, i) => `${i + 1}人`)
const weekdays = ['日', '一', '二', '三', '四', '五', '六']

const form = reactive<GenerateTripParams>({
  fromCity: '',
  toCity: '',
  days: 1,
  // 日期默认当天，不默认展示两天
  startDate: formatDateValue(new Date()),
  endDate: formatDateValue(new Date()),
  budget: 3000,
  people: 2,
  tags: []
})

const dateRangeText = computed(() => {
  if (!form.startDate || !form.endDate) return ''
  return `${formatDateLabel(form.startDate)} - ${formatDateLabel(form.endDate)}`
})

/** 是否已具备跳转条件（已选目的地）：决定"智能规划"按钮深色可点状态 */
const canGoNext = computed(() => destination.value.length > 0)

const calendarMonths = computed(() => {
  const base = new Date()
  base.setDate(1)
  return Array.from({ length: 6 }, (_, i) => buildMonth(addMonths(base, i)))
})

onMounted(() => {
  // 进入向导即开始新的规划上下文，不能复用上一行程的聊天会话。
  // pendingTripContext 属于本次外部入口上下文，由提交时单独消费。
  tripStore.resetForNewTrip()

  if (!isLoggedIn()) {
    uni.showToast({ title: '请先登录后创建行程', icon: 'none' })
    setTimeout(() => {
      redirectToLogin()
    }, 500)
    return
  }

  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as { options?: { city?: string } }
  if (page.options?.city) {
    const city = decodeURIComponent(page.options.city)
    if (city && !destination.value) {
      destination.value = city.replace(/之旅|漫步|经典.*/, '').slice(0, 6) || city
    }
  }
})

function prefIdToTag(id: string): string {
  const map: Record<string, string> = {
    classic: '经典必玩',
    food: '美食',
    photo: '摄影',
    nature: '自然风光',
    history: '历史文化',
    shop: '购物娱乐',
    walk: 'CityWalk',
    art: '文艺展览',
    niche: '小众探索'
  }
  return map[id] || id
}

function syncFormTags() {
  form.tags = selectedPrefs.value.map(prefIdToTag)
}

function addCityFromInput() {
  const val = destinationInput.value.trim()
  if (!val) return
  destination.value = val
  destinationInput.value = ''
}

// —— 城市选择面板 ——
function openCityPicker() {
  showCityPicker.value = true
}

function closeCityPicker() {
  showCityPicker.value = false
  cityKeyword.value = ''
  cityResults.value = []
}

function onCitySearch() {
  cityResults.value = searchCities(cityKeyword.value)
}

function selectProvince(name: string) {
  activeProvince.value = name
}

function pickCity(city: string) {
  destination.value = city
  destinationInput.value = ''
  cityKeyword.value = ''
  cityResults.value = []
  closeCityPicker()
}

function togglePref(id: string) {
  const idx = selectedPrefs.value.indexOf(id)
  if (idx >= 0) selectedPrefs.value.splice(idx, 1)
  else selectedPrefs.value.push(id)
}

function onPeopleChange(e: { detail: { value: number } }) {
  form.people = e.detail.value + 1
}

/** 预算输入过滤：仅保留非负数字，避免出现负数或非法字符 */
function onBudgetInput(e: any) {
  const cleaned = e.detail.value.replace(/\D/g, '')
  form.budget = cleaned === '' ? 0 : Number(cleaned)
}

function goBack() {
  uni.navigateBack()
}

function createEmpty() {
  if (!checkLogin()) return
  uni.navigateTo({ url: '/pages/trip/edit' })
}

function openCalendar() {
  tempStartDate.value = form.startDate || todayValue()
  tempEndDate.value = form.endDate && form.endDate !== tempStartDate.value ? form.endDate : ''
  isFlexibleDays.value = !!form.startDate && !!form.endDate && form.startDate !== form.endDate
  showCalendar.value = true
}

function closeCalendar() {
  showCalendar.value = false
}

function selectDate(value: string) {
  if (value < todayValue()) return
  if (!isFlexibleDays.value) {
    tempStartDate.value = value
    tempEndDate.value = value
    return
  }
  if (!tempStartDate.value || (tempStartDate.value && tempEndDate.value)) {
    tempStartDate.value = value
    tempEndDate.value = ''
    return
  }
  if (value < tempStartDate.value) {
    tempStartDate.value = value
    return
  }
  tempEndDate.value = value
}

function toggleFlexibleDays() {
  isFlexibleDays.value = !isFlexibleDays.value
  if (!isFlexibleDays.value && tempStartDate.value) {
    tempEndDate.value = tempStartDate.value
  } else if (isFlexibleDays.value && tempStartDate.value === tempEndDate.value) {
    tempEndDate.value = ''
  }
}

function confirmCalendar() {
  if (!tempStartDate.value) {
    uni.showToast({ title: '请选择出发时间', icon: 'none' })
    return
  }
  if (isFlexibleDays.value && !tempEndDate.value) {
    uni.showToast({ title: '请选择完整行程时间', icon: 'none' })
    return
  }
  form.startDate = tempStartDate.value
  form.endDate = isFlexibleDays.value ? tempEndDate.value : tempStartDate.value
  form.days = diffDays(form.startDate, form.endDate)
  closeCalendar()
}

function isSelectedDate(value: string) {
  return value === tempStartDate.value || (!!tempEndDate.value && value === tempEndDate.value)
}

function isInRange(value: string) {
  return !!tempStartDate.value && !!tempEndDate.value && value > tempStartDate.value && value < tempEndDate.value
}

function todayValue() {
  return formatDateValue(new Date())
}

function addMonths(date: Date, amount: number) {
  const next = new Date(date)
  next.setMonth(date.getMonth() + amount)
  return next
}

function buildMonth(date: Date) {
  const year = date.getFullYear()
  const month = date.getMonth()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  return {
    key: `${year}-${month + 1}`,
    title: `${year}年${month + 1}月`,
    firstDay: new Date(year, month, 1).getDay(),
    days: Array.from({ length: daysInMonth }, (_, i) => {
      const day = i + 1
      const value = formatDateValue(new Date(year, month, day))
      return {
        day,
        value,
        disabled: value < todayValue()
      }
    })
  }
}

function formatDateValue(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatDateLabel(value: string) {
  const [, month, day] = value.split('-')
  return `${Number(month)}月${Number(day)}日`
}

function diffDays(start: string, end: string) {
  const startTime = new Date(`${start}T00:00:00`).getTime()
  const endTime = new Date(`${end}T00:00:00`).getTime()
  return Math.floor((endTime - startTime) / 86400000) + 1
}

function goChat() {
	  if (!checkLogin()) return

	  if (!destination.value) {
	    addCityFromInput()
	  }
	  if (!destination.value) {
	    uni.showToast({ title: '请选择目的地', icon: 'none' })
	    return
	  }

	  // 未选日期时自动兜底：默认当天出发，按天数计算结束日期
	  if (!form.startDate) {
	    form.startDate = todayValue()
	  }
	  if (!form.endDate) {
	    const start = new Date(`${form.startDate}T00:00:00`)
	    const end = new Date(start)
	    end.setDate(start.getDate() + form.days - 1)
	    form.endDate = formatDateValue(end)
	  }
	  if (form.endDate < form.startDate) {
	    form.endDate = form.startDate
	    form.days = 1
	  }

	  form.toCity = destination.value
	  if (!form.fromCity) form.fromCity = '当前城市'
	  // 预算约束：不允许负数预算进入智能规划
	  if (!Number.isFinite(form.budget) || form.budget < 0) {
	    uni.showToast({ title: '预算不能为负数', icon: 'none' })
	    return
	  }
	  syncFormTags()

  // 提交时再次重置，覆盖同一向导页重复规划不同城市的场景。
  tripStore.resetForNewTrip()
  tripStore.currentTrip = {
    title: `${form.toCity}${form.days}日之旅`,
    fromCity: form.fromCity,
    toCity: form.toCity,
    days: form.days,
    startDate: form.startDate,
    endDate: form.endDate,
    budget: form.budget,
    people: form.people,
    tags: [...form.tags],
    // 外部带入的行程上下文（如游记/景点），随行程进入 AI 第一轮对话
    contextNote: tripStore.pendingTripContext || '',
    dayPlans: []
  }
  // 上下文只消费一次，避免残留到下一次创建
  tripStore.pendingTripContext = ''

	  uni.navigateTo({ url: '/pages/ai/chat' })
	}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
}

.nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx;
  box-sizing: border-box;
  background: var(--bg-page);
}

.back {
  font-size: 56rpx;
  font-weight: 300;
  color: var(--text-main);
  width: 80rpx;
}

.nav-btn {
  background: #000;
  color: #fff;
  font-size: var(--fs-meta);
  padding: 14rpx 28rpx;
  border-radius: 999rpx;
}

.body {
  height: 100vh;
  padding: 0 32rpx;
  box-sizing: border-box;
}

.block {
  margin-bottom: 48rpx;
}

.block-title {
  font-size: var(--fs-heading);
  font-weight: 600;
  display: block;
  margin-bottom: 24rpx;
}

.input-box {
  background: var(--bg-card);
  border-radius: 28rpx;
  border: 2rpx solid $accent-border;
  padding: 24rpx;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.city-tag {
  display: flex;
  align-items: center;
  gap: 8rpx;
  background: var(--bg-muted);
  padding: 10rpx 20rpx;
  border-radius: 12rpx;
  font-size: var(--fs-body);
}

.remove {
  color: var(--text-tertiary);
  font-size: var(--fs-title);
}

.input-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.ai-icon {
  font-size: var(--fs-subhead);
  color: #7c3aed;
}

.input {
  flex: 1;
  font-size: var(--fs-body);
  height: 64rpx;
}

.placeholder {
  color: var(--text-tertiary);
}

.picker-row {
  display: flex;
  align-items: center;
  background: var(--bg-card);
  border-radius: 28rpx;
  padding: 28rpx 32rpx;
}

.picker-icon {
  font-size: var(--fs-title);
  margin-right: 16rpx;
}

.picker-value {
  font-size: var(--fs-body);
}

.duration-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.date-range {
  font-size: var(--fs-meta);
  color: var(--text-secondary);
}

.picker-arrow {
  color: var(--text-tertiary);
  font-size: var(--fs-title);
}

.pref-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
}

.pref-chip {
  background: var(--bg-card);
  border-radius: 20rpx;
  padding: 20rpx 12rpx;
  text-align: center;
  font-size: var(--fs-meta);
  border: 3rpx solid transparent;
  transition: border-color 0.15s;

  &.active {
    border-color: var(--text-main);
  }

  &:active {
    transform: scale(0.97);
  }
}

.block.extra {
  background: var(--bg-card);
  border-radius: 24rpx;
  padding: 8rpx 24rpx;
}

.extra-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 1rpx solid var(--border);

  &:last-child {
    border-bottom: none;
  }
}

.extra-label {
  font-size: var(--fs-body);
  color: var(--text-secondary);
}

.extra-value,
.extra-input {
  font-size: var(--fs-body);
  text-align: right;
}

.extra-input {
  width: 240rpx;
}

.footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20rpx 32rpx;
  background: var(--bg-page);
}

.btn-black {
  width: 100%;
  font-size: var(--fs-subhead);
}

// 未填写目的地：浅色灰绿，表示暂不能进入下一步
.btn-black:not(.ready) {
  background: #d8e9e1;
  color: #8aa79d;
  box-shadow: none;
}

// 已填写目的地：深绿色加深，表示可以点击进入 AI 对话
.btn-black.ready {
  background: linear-gradient(90deg, #48bb88, #1f7a54);
  box-shadow: 0 8rpx 24rpx rgba(31, 122, 84, 0.3);
}

.calendar-mask {
  position: fixed;
  inset: 0;
  z-index: 300;
  background: rgba(0, 0, 0, 0.58);
  display: flex;
  align-items: flex-end;
}

.calendar-panel {
  width: 100%;
  max-height: 86vh;
  background: var(--bg-card);
  border-radius: 36rpx 36rpx 0 0;
  padding: 18rpx 40rpx 32rpx;
  box-sizing: border-box;
  animation: calendarUp 0.24s ease-out;
}

@keyframes calendarUp {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

.calendar-handle {
  width: 64rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: var(--bg-input);
  margin: 0 auto 36rpx;
}

.calendar-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
  margin-bottom: 30rpx;
}

.calendar-title {
  display: block;
  font-size: var(--fs-heading);
  font-weight: 600;
  line-height: 1.2;
}

.calendar-timezone {
  display: block;
  margin-top: 30rpx;
  color: var(--text-tertiary);
  font-size: var(--fs-meta);
}

.flex-toggle {
  display: flex;
  align-items: center;
  gap: 10rpx;
  color: var(--text-tertiary);
  background: var(--bg-muted);
  border-radius: 999rpx;
  padding: 14rpx 22rpx;
  font-size: var(--fs-meta);
  flex-shrink: 0;

  &.active {
    color: var(--text-body);
    background: var(--tag-a);

    .toggle-dot {
      background: #a8e6cf;

      &::after {
        left: 16rpx;
      }
    }
  }
}

.toggle-dot {
  width: 44rpx;
  height: 32rpx;
  border-radius: 999rpx;
  background: var(--bg-input);
  position: relative;
  transition: background 0.18s ease;

  &::after {
    content: '';
    position: absolute;
    left: 4rpx;
    top: 4rpx;
    width: 24rpx;
    height: 24rpx;
    border-radius: 50%;
    background: var(--bg-card);
    transition: left 0.18s ease;
  }
}

.calendar-scroll {
  height: 56vh;
}

.month-block {
  padding-bottom: 44rpx;
}

.month-title {
  display: block;
  font-size: var(--fs-subhead);
  font-weight: 600;
  margin-bottom: 34rpx;
}

.weekday-grid,
.date-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}

.weekday {
  text-align: center;
  color: var(--text-tertiary);
  font-size: var(--fs-meta);
  margin-bottom: 24rpx;
}

.date-cell {
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-subhead);
  position: relative;
  z-index: 1;
  transition: background 0.15s ease;

  text {
    width: 64rpx;
    height: 64rpx;
    line-height: 64rpx;
    text-align: center;
    border-radius: 50%;
    position: relative;
    z-index: 2;
    transition: background 0.15s ease, color 0.15s ease;
  }

  // 区间中间天数：深青色连续滑块带（左右贯通整格，与端点圆相接不留白）
  &.inRange::before {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    top: 8rpx;
    bottom: 8rpx;
    background: rgba(13, 148, 136, 0.22);
  }

  // 起止日期：深青色实心圆 + 白字，颜色醒目
  &.selected text {
    background: #0d9488;
    color: #fff;
    font-weight: 600;
  }

  &.disabled {
    color: var(--text-tertiary);
  }
}

.calendar-confirm {
  width: 320rpx;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 999rpx;
  background: #000;
  color: #fff;
  font-size: var(--fs-body);
  margin: 18rpx auto 0;
  border: none;

  &::after {
    border: none;
  }
}

/* ===== 城市选择面板 ===== */
.pick-hint {
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.city-mask {
  position: fixed;
  inset: 0;
  z-index: 300;
  background: rgba(0, 0, 0, 0.58);
  display: flex;
  align-items: flex-end;
}

.city-panel {
  width: 100%;
  max-height: 82vh;
  background: var(--bg-card);
  border-radius: 36rpx 36rpx 0 0;
  padding: 18rpx 32rpx 32rpx;
  box-sizing: border-box;
  animation: calendarUp 0.24s ease-out;
  display: flex;
  flex-direction: column;
}

.city-handle {
  width: 64rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: var(--bg-input);
  margin: 0 auto 24rpx;
}

.city-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.city-title {
  font-size: var(--fs-heading);
  font-weight: 600;
}

.city-close {
  font-size: var(--fs-title);
  color: var(--text-tertiary);
  padding: 8rpx;
}

.city-search {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: var(--bg-input);
  border-radius: 999rpx;
  padding: 16rpx 28rpx;
  margin-bottom: 20rpx;
}

.search-icon {
  font-size: var(--fs-body);
}

.city-search-input {
  flex: 1;
  font-size: var(--fs-body);
  color: var(--text-main);
}

.search-clear {
  font-size: var(--fs-body);
  color: var(--text-tertiary);
  padding: 4rpx 8rpx;
}

.city-search-results {
  max-height: 56vh;
  flex: 1;
}

.city-result-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 12rpx;
  border-bottom: 1rpx solid var(--border);
}

.result-city {
  font-size: var(--fs-title);
  color: var(--text-main);
}

.result-prov {
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
}

.city-empty {
  text-align: center;
  color: var(--text-tertiary);
  font-size: var(--fs-body);
  padding: 48rpx 0;
}

.hot-section {
  margin-bottom: 20rpx;
}

.hot-label {
  display: block;
  font-size: var(--fs-body);
  color: var(--text-secondary);
  margin-bottom: 14rpx;
}

.hot-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.hot-chip {
  background: var(--bg-muted);
  border-radius: 999rpx;
  padding: 12rpx 26rpx;
  font-size: var(--fs-body);
  color: var(--text-body);

  &.picked {
    background: rgba(13, 148, 136, 0.15);
    color: #0d9488;
  }
}

.prov-city {
  display: flex;
  flex: 1;
  min-height: 0;
  height: 46vh;
}

.prov-list {
  width: 200rpx;
  flex-shrink: 0;
  border-right: 1rpx solid var(--border);
}

.prov-item {
  padding: 20rpx 16rpx;
  font-size: var(--fs-body);
  color: var(--text-secondary);

  &.active {
    color: #0d9488;
    font-weight: 600;
    background: rgba(13, 148, 136, 0.08);
    border-left: 6rpx solid #0d9488;
  }
}

.city-list {
  flex: 1;
  padding-left: 20rpx;
}

.city-item {
  padding: 18rpx 12rpx;
  font-size: var(--fs-body);
  color: var(--text-main);

  &.picked {
    color: #0d9488;
    font-weight: 600;
  }
}

</style>
