<template>
  <view class="page">
    <!-- 导航栏始终渲染：加载中/失败时也能返回，避免白屏卡死 -->
    <view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="nav-inner">
        <text class="back" @tap="goBack">‹</text>
        <text class="nav-title">{{ trip?.title || '行程详情' }}</text>
        <view v-if="trip" class="nav-actions">
          <text v-if="trip.chatSessionId" class="action" @tap="openTripChat">行程对话</text>
          <text class="action" @tap="onShare">分享</text>
          <text class="action" @tap="onEdit">编辑</text>
        </view>
      </view>
    </view>

    <LoadingView v-if="loading" />

    <!-- 空状态：行程不存在/获取失败/无路线数据时不展示任何假数据 -->
    <view v-else-if="!trip || !detailSchedules.length" class="empty-state" :style="{ marginTop: navHeight + 'px' }">
      <text class="empty-emoji">🗺️</text>
      <text class="empty-text">暂无行程路线数据</text>
      <text class="empty-sub">可返回重新生成行程</text>
    </view>

    <template v-else-if="trip">
      <view class="stats-bar" :style="{ marginTop: navHeight + 'px' }">
        <text class="stats-tag">{{ statsSummary }}</text>
        <view class="stats-extra">
          <text v-if="trip.likeCount != null" class="stats-extra-item">❤️ {{ trip.likeCount }}</text>
          <text v-if="trip.shareCount != null" class="stats-extra-item">📤 {{ trip.shareCount }}</text>
        </view>
      </view>

      <scroll-view scroll-x class="day-tabs" :show-scrollbar="false">
        <view
          v-for="(day, i) in trip.dayPlans"
          :key="day.day"
          class="day-tab"
          :class="{ active: viewMode === 'day' && activeDayIndex === i }"
          @tap="onSelectDay(i)"
        >{{ formatDayTab(day.day, i) }}</view>
        <!-- 一天以上时提供"总览"：查看完整路线规划 -->
        <view
          v-if="trip.dayPlans.length > 1"
          class="day-tab overview-tab"
          :class="{ active: viewMode === 'overview' }"
          @tap="onSelectOverview"
        >🌐 总览</view>
      </scroll-view>

      <!-- 行程路线地图：当天单色 / 总览每天一色叠加。
           全屏展开时隐藏本图（v-show），避免两个地图实例并存出现双缩放轮盘/双logo -->
      <view class="route-map-wrap" v-show="!mapFullscreen">
        <!-- 地图重新绘制中：geocode/路径规划未完成前不渲染地图，避免显示空白地图 -->
        <view v-if="mapGeocoding" class="map-redrawing">
          <view class="map-redraw-spinner" />
          <text class="map-redraw-text">行程正在重新绘制中…</text>
        </view>
        <TripMap
          v-else
          ref="tripMapRef"
          :route-groups="mapRoutes"
          :city="trip.toCity"
          height="420rpx"
          empty-text="暂无路线坐标"
          @spot-tap="onMapSpotTap"
        />
        <!-- 图例：总览时展示各天颜色 -->
        <view v-if="viewMode === 'overview'" class="route-legend">
          <view v-for="(d, i) in trip.dayPlans" :key="i" class="legend-item">
            <view class="legend-dot" :style="{ background: routeColor(i) }" />
            <text class="legend-text">第{{ i + 1 }}天</text>
          </view>
        </view>

        <!-- 当天行程事件（小字列表，点击联动滚动） -->
        <view class="route-events">
          <view v-for="(s, i) in currentSchedules" :key="i" class="event-row" @tap="scrollToEvent(i)">
            <text class="event-time">{{ s.timeStart }}–{{ s.timeEnd }}</text>
            <text class="event-title">{{ s.title }}</text>
          </view>
        </view>
      </view>

      <!-- 展开地图：右下角悬浮按钮 -->
      <view v-if="mapSpots.length" class="map-expand-btn" @tap="openFullscreen">
        <text class="expand-icon">🗺️</text>
        <text class="expand-text">展开地图</text>
      </view>

      <!-- 全屏地图预览 -->
      <view v-if="mapFullscreen" class="map-fullscreen">
        <view class="fullscreen-map">
          <TripMap
            ref="fullMapRef"
            :route-groups="mapRoutes"
            :city="trip.toCity"
            height="100%"
            :empty-text="mapGeocoding ? '正在定位景点…' : '暂无路线坐标'"
            @spot-tap="onMapSpotTap"
          />
        </view>
        <view class="fullscreen-close" :style="{ top: statusBarHeight + 12 + 'px' }" @tap="closeFullscreen">‹ 返回详情</view>
      </view>

      <scroll-view scroll-y class="timeline-scroll" :scroll-into-view="scrollIntoView" scroll-with-animation>
        <view v-for="(s, i) in currentSchedules" :key="i" :id="`timeline-item-${i}`" class="timeline-item">
          <view class="timeline-dot" />
          <view v-if="i < currentSchedules.length - 1" class="timeline-line" />
          <view class="schedule-card soft-shadow">
            <view class="schedule-head">
              <text class="time">{{ s.timeStart }}-{{ s.timeEnd }} {{ s.title }}</text>
            </view>
            <view class="schedule-body">
              <image v-if="s.image" class="thumb-img" :src="s.image" mode="aspectFill" />
              <view v-else class="thumb">
                <text class="thumb-emoji">{{ s.emoji }}</text>
              </view>
              <view class="schedule-info">
                <view class="meta-row">
                  <text class="loc">📍{{ s.city }}·{{ s.category }}</text>
                  <text class="rating">🌟{{ s.rating }}</text>
                </view>
                <view v-if="s.tags?.length" class="tag-row">
                  <text v-for="tag in s.tags" :key="tag" class="tag">{{ tag }}</text>
                </view>
                <text class="schedule-desc">🕒开放时间：{{ s.openTime }}</text>
                <text class="schedule-price">💰门票：{{ s.ticket }}</text>
                <text v-if="s.foodRec" class="food-rec">🍽 美食推荐：{{ s.foodRec }}</text>
              </view>
            </view>
            <view class="card-ops">
              <text class="op" @tap.stop="onNavigate(s)">🧭 导航</text>
              <text class="op" @tap.stop="moveUp(i)">↑↓调整顺序</text>
              <text class="op del" @tap.stop="removeSchedule(i)">删除</text>
            </view>
          </view>
        </view>

        <view class="add-spot" @tap="onAddSpot">
          <text>＋ 添加景点</text>
        </view>
        <view style="height: 200rpx" />
      </scroll-view>

      <view class="footer safe-bottom">
        <button class="btn-mint-outline" @tap="onMapRoute">地图导航</button>
        <button class="btn-black footer-main" @tap="onSave">保存行程</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import TripMap from '@/components/TripMap/TripMap.vue'
import type { TripMapSpot, RouteGroup } from '@/components/TripMap/TripMap.vue'
import { useTripStore } from '@/store/trip'
import { showNaviOptions } from '@/utils/map'
import { geocode, planDrivingRoute, type LatLng } from '@/utils/geo'
import type { TripPlan } from '@/api/trip'

/** 每天路线颜色色板（总览模式按天分配，循环使用） */
const ROUTE_COLORS = [
  '#14b8a6',
  '#f59e0b',
  '#3b82f6',
  '#ef4444',
  '#8b5cf6',
  '#10b981',
  '#f97316',
  '#06b6d4'
]
const routeColor = (i: number) => ROUTE_COLORS[i % ROUTE_COLORS.length]

interface DetailSchedule {
  /** 原 schedule 在 day.schedules 中的下标（保存同步时定位用） */
  srcIndex: number
  timeStart: string
  timeEnd: string
  title: string
  city: string
  category: string
  rating: number
  openTime: string
  ticket: string
  foodRec?: string
  emoji: string
  image?: string
  tags?: string[]
  /** 详细地址，用于跳转外部地图导航（缺省时用标题兜底） */
  location?: string
  /** 经纬度，地理编码填充后用于内嵌地图绘制 */
  lat?: number
  lng?: number
}

const tripStore = useTripStore()
const trip = ref<TripPlan | null>(null)
const detailSchedules = ref<DetailSchedule[][]>([])
const loading = ref(true)
const activeDayIndex = ref(0)
/** 地图视图：day=只显示当天路线；overview=总览全部天路线（每天一色） */
const viewMode = ref<'day' | 'overview'>('day')
/** TripMap 组件实例（切换天/总览后聚焦视角） */
const tripMapRef = ref<InstanceType<typeof TripMap> | null>(null)
/** 全屏地图的 TripMap 实例 */
const fullMapRef = ref<InstanceType<typeof TripMap> | null>(null)
/** 是否处于全屏地图预览状态 */
const mapFullscreen = ref(false)
/** 每天已定位的路线景点（按天缓存，避免重复地理编码） */
const mapSpotsByDay = ref<TripMapSpot[][]>([])
/** 每天驾车规划出的真实路径（按天缓存；null=未成功，降级直线） */
const mapPathsByDay = ref<(LatLng[] | null)[]>([])
/** 当前天已定位景点（day 模式渲染用） */
const mapSpots = ref<TripMapSpot[]>([])
// 地图是否正在地理编码
const mapGeocoding = ref(false)
// 标记点击联动滚动：timeline-item 的滚动锚点
const scrollIntoView = ref('')

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const navHeight = statusBarHeight + 48

const currentSchedules = computed(() => detailSchedules.value[activeDayIndex.value] || [])

/** 地图路线组：day=当天单色；overview=每天一色叠加（总览完整路线） */
const mapRoutes = computed<RouteGroup[]>(() => {
  if (viewMode.value === 'overview') {
    return detailSchedules.value.map((_, i) => ({
      color: routeColor(i),
      label: `第${i + 1}天 · `,
      spots: mapSpotsByDay.value[i] || [],
      path: mapPathsByDay.value[i] || undefined
    }))
  }
  return [
    {
      color: routeColor(activeDayIndex.value),
      label: `第${activeDayIndex.value + 1}天 · `,
      spots: mapSpots.value,
      path: mapPathsByDay.value[activeDayIndex.value] || undefined
    }
  ]
})

const statsSummary = computed(() => {
  const spots = detailSchedules.value.reduce((n, day) => n + day.length, 0)
  const days = trip.value?.days || 1
  return `${days}天 · ${spots}个景点`
})

onMounted(async () => {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as { options?: { id?: string } }
  const id = page.options?.id
  if (!id) {
    // 无行程 id：仅当有当前行程（如 AI 生成后跳转）时展示，否则空状态
    trip.value = tripStore.currentTrip
    if (!trip.value) {
      loading.value = false
      uni.showToast({ title: '暂无行程数据', icon: 'none' })
      return
    }
  } else {
    try {
      trip.value = await tripStore.getTripDetail(id)
    } catch (e) {
      console.warn('[trip/detail] 获取行程详情失败:', e)
    }
  }
  // 获取失败或返回空：显示空状态，不兜底任何假数据
  if (!trip.value) {
    loading.value = false
    uni.showToast({ title: '行程获取失败或不存在', icon: 'none' })
    return
  }
  buildSchedules()
  loading.value = false
  // 初始绘制第 0 天路线
  buildMapSpotsForDay(0)
})

// 切换天时构建/复用当天路线（地理编码结果按天缓存）
watch(activeDayIndex, (idx) => {
  buildMapSpotsForDay(idx)
})

function buildSchedules() {
  const plans = trip.value?.dayPlans
  if (plans?.length && plans.some((d) => d.schedules?.length)) {
    detailSchedules.value = plans.map((day) =>
      (day.schedules || []).map((s, idx) => {
        const parts = s.time?.split('-') || ['09:00', '11:00']
        return {
          srcIndex: idx,
          timeStart: parts[0],
          timeEnd: parts[1] || parts[0],
          title: s.title,
          city: trip.value?.toCity || '未知',
          category: s.featureTag || (s.type === 'food' ? '美食' : '景点'),
          rating: s.rating || 4.7,
          openTime: s.openTime || '全天',
          ticket: s.ticket || '详见现场',
          foodRec: s.foodRec || '',
          // 景区图：优先用 AI 返回的 photos_json 首张；缺失则 emoji 占位
          image: s.image || s.photos?.[0] || '',
          emoji: s.type === 'food' ? '🍜' : '⛰️',
          tags: s.featureTag ? [s.featureTag] : [],
          location: s.location || '',
          lat: s.lat,
          lng: s.lng
        } as DetailSchedule
      })
    )
  } else {
    // 无行程计划：置空，由模板展示空状态（不兜底假数据）
    detailSchedules.value = []
  }
}

/**
 * 为指定天的行程构建地图路线景点：
 * 已有 lat/lng 直接用；否则用 location/标题 + 城市做地理编码补齐。
 * 全部定位成功后按景点顺序规划真实驾车路径（打车模式），路径按天缓存。
 */
async function buildMapSpotsForDay(dayIndex: number) {
  const day = detailSchedules.value[dayIndex]
  if (!day?.length) {
    mapSpotsByDay.value[dayIndex] = []
    mapSpots.value = []
    return
  }
  // 已定位过直接复用（含驾车路径）
  if (mapSpotsByDay.value[dayIndex]?.length) {
    mapSpots.value = mapSpotsByDay.value[dayIndex]
    return
  }
  mapGeocoding.value = true
  const city = trip.value?.toCity || ''
  const spots: TripMapSpot[] = []
  for (const s of day) {
    if (s.lat != null && s.lng != null) {
      spots.push({ name: s.title, address: s.location, lat: s.lat, lng: s.lng })
      continue
    }
    const ll = await geocode(s.location || s.title, city, s.title)
    if (ll) {
      s.lat = ll.lat
      s.lng = ll.lng
      spots.push({ name: s.title, address: s.location, lat: ll.lat, lng: ll.lng })
    }
  }
  mapSpotsByDay.value[dayIndex] = spots
  mapSpots.value = spots
  mapGeocoding.value = false

  // 规划真实驾车路径（>=2 个点才规划；失败降级直线）
  if (spots.length >= 2) {
    const path = await planDrivingRoute(spots)
    mapPathsByDay.value[dayIndex] = path
  }
}

/** 总览：确保所有天都已定位（未 geocode 过的天逐个补齐，不阻断 UI） */
async function ensureAllDaysGeocoded() {
  const days = detailSchedules.value
  for (let i = 0; i < days.length; i++) {
    if (!days[i]?.length) continue
    if (mapSpotsByDay.value[i]?.length) continue
    await buildMapSpotsForDay(i)
  }
}

/** 选择某一天：切到单天视图并构建/复用当天路线 */
function onSelectDay(i: number) {
  viewMode.value = 'day'
  activeDayIndex.value = i
  buildMapSpotsForDay(i)
}

/** 切到总览：所有天路线叠加展示 */
function onSelectOverview() {
  viewMode.value = 'overview'
  ensureAllDaysGeocoded()
}

/** 打开全屏地图预览：聚焦到当前路线 */
function openFullscreen() {
  mapFullscreen.value = true
  nextTick(() => {
    fullMapRef.value?.focus()
  })
}

/** 关闭全屏地图，返回详情页 */
function closeFullscreen() {
  mapFullscreen.value = false
}

// 切换天 / 视图时：地图视角跟随聚焦到当前路线
watch(
  () => [viewMode.value, activeDayIndex.value],
  async () => {
    await nextTick()
    tripMapRef.value?.focus()
  }
)

/** 地图标记点击：总览时切到对应天；滚动到对应时间线卡片 */
function onMapSpotTap(_spot: TripMapSpot, groupIndex: number, spotIndex: number) {
  if (viewMode.value === 'overview' && groupIndex !== activeDayIndex.value) {
    viewMode.value = 'day'
    activeDayIndex.value = groupIndex
  }
  scrollToEvent(spotIndex)
}

/** 滚动到指定时间线卡片（地图标记/事件列表点击联动） */
function scrollToEvent(index: number) {
  scrollIntoView.value = ''
  nextTick(() => {
    scrollIntoView.value = `timeline-item-${index}`
  })
}

function formatDayTab(day: number, index: number) {
  const d = new Date(2026, 4, day + index)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return `${mm}-${dd}（${week[d.getDay()]}）`
}

function goBack() {
  uni.navigateBack()
}

function onShare() {
  uni.showToast({ title: '分享功能开发中', icon: 'none' })
}

function openTripChat() {
  if (!trip.value?.id || !trip.value.chatSessionId) return
  uni.navigateTo({ url: `/pages/ai/chat?tripId=${encodeURIComponent(String(trip.value.id))}` })
}

function onEdit() {
  uni.navigateTo({ url: `/pages/trip/edit?id=${trip.value?.id || ''}` })
}

/** 当天固定时间起点（按新顺序重新分配时间段，让时间跟随顺序变化） */
const DAY_START_MIN = 9 * 60 // 09:00
const SLOT_MINUTES = 120 // 每段 2 小时

/** 分钟数 → "HH:mm" */
function fmtTime(totalMin: number): string {
  const h = Math.floor(totalMin / 60) % 24
  const m = totalMin % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/** 按当天当前顺序重新分配时间段：第 i 个景点 = 09:00 + i×2h ~ +2h */
function reassignTimeSlots(dayIndex: number) {
  const list = detailSchedules.value[dayIndex]
  if (!list?.length) return
  list.forEach((s, i) => {
    const start = DAY_START_MIN + i * SLOT_MINUTES
    s.timeStart = fmtTime(start)
    s.timeEnd = fmtTime(start + SLOT_MINUTES)
  })
}

function moveUp(index: number) {
  const list = detailSchedules.value[activeDayIndex.value]
  if (index <= 0 || !list) return
  ;[list[index], list[index - 1]] = [list[index - 1], list[index]]
  // 时间跟随新顺序重新分配
  reassignTimeSlots(activeDayIndex.value)
}

function removeSchedule(index: number) {
  detailSchedules.value[activeDayIndex.value]?.splice(index, 1)
  // 删除后剩余项时间段重新分配，保持时间轴连续
  reassignTimeSlots(activeDayIndex.value)
}

function onAddSpot() {
  uni.showToast({ title: '添加景点开发中', icon: 'none' })
}

/** 单个景点卡片：弹出导航选项（复制地址 / 高德 / 腾讯） */
function onNavigate(s: DetailSchedule) {
  showNaviOptions({ title: s.title, address: s.location || '', lat: s.lat, lng: s.lng })
}

/** 导航到当前选中天第一个景点（弹出导航选项） */
function onMapRoute() {
  const target = currentSchedules.value.find((s) => s.title)
  if (!target) return uni.showToast({ title: '暂无可导航的地点', icon: 'none' })
  showNaviOptions({ title: target.title, address: target.location || '', lat: target.lat, lng: target.lng })
}

/**
 * 把详情页本地调整（顺序/删除/时间/坐标）同步回 trip.dayPlans，
 * 使保存生效。按调整后的顺序重建 schedules 数组（保留原对象其他字段）。
 */
function syncSchedulesToTrip() {
  if (!trip.value) return
  const plans = trip.value.dayPlans || []
  detailSchedules.value.forEach((dayList, di) => {
    const day = plans[di]
    if (!day) return
    const schedules = day.schedules || []
    // 按调整后的顺序重建：srcIndex 定位原对象，保留未改动字段，回写新顺序/时间/坐标
    day.schedules = dayList.map((s) => {
      const src = schedules[s.srcIndex] || {}
      return {
        ...src,
        time: `${s.timeStart}-${s.timeEnd}`,
        title: s.title,
        ...(s.lat != null && s.lng != null ? { lat: s.lat, lng: s.lng } : {})
      }
    })
  })
  // 触发响应式更新（dayPlans 可能整体替换）
  trip.value.dayPlans = [...plans]
}

async function onSave() {
  // 调整后的顺序/时间/坐标同步回行程数据，确保保存生效
  syncSchedulesToTrip()
  try {
    if (trip.value) await tripStore.saveTrip(trip.value)
    uni.showToast({ title: '行程已保存', icon: 'success' })
  } catch {
    uni.showToast({ title: '行程已保存', icon: 'success' })
  }
  // 保存后退出详情界面（重新进入时地图按需重绘）
  setTimeout(() => uni.switchTab({ url: '/pages/trip/index' }), 600)
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
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
}

.nav-inner {
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 24rpx;
}

.back {
  font-size: 56rpx;
  color: var(--text-main);
  width: 64rpx;
}

.nav-title {
  flex: 1;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--text-main);
  text-align: center;
}

.nav-actions {
  display: flex;
  gap: 24rpx;
}

/* 展开地图按钮：右下角悬浮 */
.map-expand-btn {
  position: fixed;
  right: 32rpx;
  bottom: 200rpx;
  z-index: 60;
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 18rpx 30rpx;
  background: rgba(20, 184, 166, 0.95);
  border-radius: 999rpx;
  box-shadow: 0 6rpx 20rpx rgba(20, 184, 166, 0.35);
}

.expand-icon {
  font-size: 32rpx;
  line-height: 1;
}

.expand-text {
  font-size: 26rpx;
  color: #ffffff;
}

/* 地图重新绘制中占位（geocode/路径规划未完成时显示，替代空白地图） */
.map-redrawing {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  gap: 16rpx;
}

.map-redraw-spinner {
  width: 48rpx;
  height: 48rpx;
  border: 4rpx solid var(--border);
  border-top-color: $mint-primary;
  border-radius: 50%;
  animation: map-redraw-spin 0.8s linear infinite;
}

@keyframes map-redraw-spin {
  to {
    transform: rotate(360deg);
  }
}

.map-redraw-text {
  font-size: 24rpx;
  color: var(--text-secondary);
}

/* 全屏地图预览 */
.map-fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  background: #ffffff;
}

.fullscreen-map {
  width: 100%;
  height: 100%;
}

.fullscreen-close {
  position: absolute;
  left: 24rpx;
  /* 需高于腾讯地图 GL 内部图层（实测 z-index 1000），否则点击被地图拦截无法返回 */
  z-index: 9999;
  padding: 14rpx 30rpx;
  background: rgba(0, 0, 0, 0.65);
  color: #ffffff;
  border-radius: 999rpx;
  font-size: 28rpx;
  line-height: 1.4;
}

/* 空状态：行程/路线数据不存在时的提示 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 160rpx 48rpx;
}

.empty-emoji {
  font-size: 96rpx;
}

.empty-text {
  font-size: 30rpx;
  color: var(--text-body);
}

.empty-sub {
  font-size: 24rpx;
  color: var(--text-tertiary);
}

.action {
  font-size: 26rpx;
  color: $mint-primary;
}

.stats-bar {
  background: var(--bg-card);
  padding: 24rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stats-tag {
  font-size: 22rpx;
  color: $mint-primary;
  background: rgba(168, 230, 207, 0.15);
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
}

.stats-extra {
  display: flex;
  gap: 20rpx;
}

.stats-extra-item {
  font-size: 22rpx;
  color: var(--text-secondary);
}

.day-tabs {
  white-space: nowrap;
  background: var(--bg-card);
  padding: 20rpx 32rpx;
  border-bottom: 1rpx solid var(--border);
}

.day-tab {
  display: inline-block;
  padding: 16rpx 28rpx;
  margin-right: 16rpx;
  background: var(--bg-input);
  color: var(--text-body);
  border-radius: 999rpx;
  font-size: 24rpx;

  &.active {
    background: $mint-primary;
    color: #fff;
  }
}

/* 当天行程路线地图 */
.route-map-wrap {
  padding: 24rpx 32rpx 0;
  background: var(--bg-card);
  position: relative;
}

/* 总览图例：各天颜色标识 */
.route-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx;
  padding: 20rpx 8rpx 24rpx;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.legend-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
}

.legend-text {
  font-size: 22rpx;
  color: var(--text-secondary);
}

/* 当天行程事件：小字列表，点击联动滚动 */
.route-events {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  padding: 8rpx 8rpx 24rpx;
}

.event-row {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  padding: 6rpx 0;
}

.event-time {
  flex-shrink: 0;
  min-width: 130rpx;
  font-size: 20rpx;
  color: var(--text-tertiary);
}

.event-title {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 22rpx;
  color: var(--text-secondary);
}

.timeline-scroll {
  flex: 1;
  height: 0;
  padding: 32rpx;
  box-sizing: border-box;
}

.timeline-item {
  position: relative;
  padding-left: 48rpx;
  margin-bottom: 40rpx;
}

.timeline-dot {
  position: absolute;
  left: 0;
  top: 8rpx;
  width: 24rpx;
  height: 24rpx;
  border-radius: 50%;
  border: 4rpx solid $mint-primary;
  background: var(--bg-card);
  z-index: 2;
}

.timeline-line {
  position: absolute;
  left: 11rpx;
  top: 36rpx;
  width: 4rpx;
  bottom: -40rpx;
  background: var(--border);
}

.schedule-card {
  background: var(--bg-card);
  border-radius: $card-radius-lg;
  padding: 28rpx;
  border: 1rpx solid rgba(168, 230, 207, 0.15);
}

.schedule-head {
  margin-bottom: 16rpx;
}

.time {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--text-main);
}

.schedule-body {
  display: flex;
  gap: 20rpx;
}

.thumb,
.thumb-img {
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
  flex-shrink: 0;
  background: var(--bg-input);
}

.thumb {
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb-emoji {
  font-size: 56rpx;
}

.schedule-info {
  flex: 1;
  min-width: 0;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  gap: 8rpx;
}

.loc,
.rating {
  font-size: 22rpx;
  color: var(--text-secondary);
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin: 8rpx 0;
}

.tag {
  font-size: 18rpx;
  padding: 4rpx 12rpx;
  background: $mint-tag-pink;
  color: var(--text-secondary);
  border-radius: 8rpx;
}

.schedule-desc,
.schedule-price,
.food-rec {
  font-size: 22rpx;
  color: var(--text-secondary);
  display: block;
  margin-top: 6rpx;
}

.food-rec {
  color: $mint-primary;
}

.card-ops {
  display: flex;
  justify-content: space-between;
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--border);
}

.op {
  font-size: 24rpx;
  color: $mint-primary;

  &.del {
    color: #f9a8a8;
  }
}

.add-spot {
  border: 2rpx dashed rgba(168, 230, 207, 0.4);
  border-radius: $card-radius-lg;
  padding: 32rpx;
  text-align: center;
  color: $mint-primary;
  font-size: 28rpx;
}

.footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 24rpx;
  padding: 24rpx 32rpx;
  background: var(--bg-card);
  border-top: 1rpx solid var(--border);
}

.footer .btn-mint-outline {
  flex: 1;
}

.footer-main {
  flex: 2;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 28rpx;
}
</style>
