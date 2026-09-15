<template>
  <view class="page">
    <!-- 背景层：地图铺满整屏，下面的导航与上拉框都是叠在它上面的浮层 -->
    <view class="map-bg">
      <!-- 地图重新绘制中：geocode/路径规划未完成前不渲染地图，避免显示空白地图 -->
      <view v-if="mapGeocoding" class="map-redrawing">
        <view class="map-redraw-spinner" />
        <text class="map-redraw-text">行程正在重新绘制中…</text>
      </view>
      <TripMap
        v-else
        ref="tripMapRef"
        :route-groups="mapRoutes"
        :city="trip?.toCity || ''"
        height="100%"
        empty-text="暂无路线坐标"
        @spot-tap="onMapSpotTap"
      />
    </view>

    <!-- 浮层操作按钮：返回在左，分享 / 设置（进编辑页）在右。
         原来的整条导航栏已去掉，行程名移进了上方拉框的头部 -->
    <view class="float-actions" :style="{ top: FLOAT_BTN_TOP + 'px' }">
      <view class="icon-btn" @tap="goBack">
        <AppIcon name="chevron-left" :size="40" color="var(--text-main)" />
      </view>
      <view class="icon-btn-group">
        <view class="icon-btn" @tap="onShare">
          <AppIcon name="share" :size="34" color="var(--text-main)" />
        </view>
        <view class="icon-btn" @tap="onEdit">
          <AppIcon name="settings" :size="34" color="var(--text-main)" />
        </view>
      </view>
    </view>

    <LoadingView v-if="loading" />

    <!-- 空状态：行程不存在/获取失败/无路线数据时不展示任何假数据（盖住地图，避免与地图自身的空文案重复） -->
    <view v-else-if="!trip || !detailSchedules.length" class="empty-state">
      <text class="empty-emoji">🗺️</text>
      <text class="empty-text">暂无行程路线数据</text>
      <text class="empty-sub">可返回重新生成行程</text>
    </view>

    <template v-else-if="trip">
      <!-- 行程摘要 + 互动数：浮在地图左上（按钮行下方） -->
      <view class="stats-bar" :style="{ top: FLOAT_ROW2_TOP + 'px' }">
        <text class="stats-tag">{{ statsSummary }}</text>
        <view class="stats-extra">
          <text v-if="trip.likeCount != null" class="stats-extra-item">❤️ {{ trip.likeCount }}</text>
          <text v-if="trip.shareCount != null" class="stats-extra-item">📤 {{ trip.shareCount }}</text>
        </view>
      </view>

      <!-- 上拉框：四档（覆盖 3/4 → 1/2 → 1/4 → 不覆盖）。
           把手区整块可拖（不是只有那条细线），也支持点击依次切换四档 -->
      <view class="sheet" :style="sheetStyle">
        <view
          class="sheet-grip"
          :style="{ height: SHEET_GRIP_H + 'px' }"
          @touchstart="onSheetTouchStart"
          @touchmove.stop.prevent="onSheetTouchMove"
          @touchend="onSheetTouchEnd"
          @touchcancel="onSheetTouchEnd"
          @tap="cycleSheet"
        >
          <view class="handle-bar" />
          <text v-if="sheetCollapsed" class="grip-hint">点击展开行程</text>
        </view>

        <view class="sheet-head">
          <!-- 精选行程：说明这是别人的推荐内容、以及能做什么（也解释了为何没有编辑按钮） -->
          <view v-if="isFeaturedPreview" class="featured-tip">
            <text class="featured-chip">精选行程</text>
            <text class="featured-tip-text">可添加到我的行程，也可先对话修改再保存</text>
          </view>

          <!-- 行程名：原来在顶部导航栏，导航栏去掉后移到这里（卡片区上方） -->
          <text class="sheet-title">{{ trip.title || '行程详情' }}</text>

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

          <!-- 总览图例：各天颜色标识（框内展示，避免被上拉框遮住） -->
          <view v-if="viewMode === 'overview'" class="route-legend">
            <view v-for="(d, i) in trip.dayPlans" :key="i" class="legend-item">
              <view class="legend-dot" :style="{ background: routeColor(i) }" />
              <text class="legend-text">第{{ i + 1 }}天</text>
            </view>
          </view>
        </view>

        <scroll-view scroll-y class="sheet-body" :scroll-into-view="scrollIntoView" scroll-with-animation>
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
                <!-- 精选行程是别人的内容，只读：不提供调序/删除 -->
                <template v-if="!isFeaturedPreview">
                  <text class="op" @tap.stop="moveUp(i)">↑↓调整顺序</text>
                  <text class="op del" @tap.stop="removeSchedule(i)">删除</text>
                </template>
              </view>
            </view>
          </view>

          <view v-if="!isFeaturedPreview" class="add-spot" @tap="onAddSpot">
            <text>＋ 添加景点</text>
          </view>
          <view style="height: 40rpx" />
        </scroll-view>

        <!-- 精选行程：只提供「对话修改」与「添加到我的行程」两个动作 -->
        <view v-if="isFeaturedPreview" class="sheet-footer safe-bottom">
          <button class="btn-mint-outline" @tap="onChatModify">对话修改</button>
          <button class="btn-black footer-main" @tap="onAddToMine">添加到我的行程</button>
        </view>
        <view v-else class="sheet-footer safe-bottom">
          <button class="btn-mint-outline" @tap="onMapRoute">地图导航</button>
          <button class="btn-black footer-main" @tap="onSave">保存行程</button>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import TripMap from '@/components/TripMap/TripMap.vue'
import type { TripMapSpot, RouteGroup } from '@/components/TripMap/TripMap.vue'
import { useTripStore } from '@/store/trip'
import { showNaviOptions } from '@/utils/map'
import { geocode, planDrivingRoute, type LatLng } from '@/utils/geo'
import { clearActiveChatSession } from '@/utils/chatSession'
import { installBackGuard } from '@/utils/backGuard'
import { useLogin } from '@/hooks/useLogin'
import { getFeaturedDetail, copyFeaturedToMine } from '@/api/featured'
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
const { checkLogin } = useLogin()
const trip = ref<TripPlan | null>(null)
const detailSchedules = ref<DetailSchedule[][]>([])
const loading = ref(true)
/** 精选行程 id（有值 = 当前在预览别人的精选行程，整页只读） */
const featuredTripId = ref('')
/** 是否处于精选行程预览（只读）模式 */
const isFeaturedPreview = computed(() => !!featuredTripId.value)
const activeDayIndex = ref(0)
/** 地图视图：day=只显示当天路线；overview=总览全部天路线（每天一色） */
const viewMode = ref<'day' | 'overview'>('day')
/** TripMap 组件实例（切换天/总览后聚焦视角） */
const tripMapRef = ref<InstanceType<typeof TripMap> | null>(null)
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
/** 浮层按钮行的顶部位置 */
const FLOAT_BTN_TOP = statusBarHeight + 8
/** 第二行浮层元素（行程摘要 / 全屏地图入口）的顶部位置：让开 72rpx 的按钮 */
const FLOAT_ROW2_TOP = statusBarHeight + 56

// ===== 上拉框（四档：覆盖 3/4 → 1/2 → 1/4 → 不覆盖只留把手）=====
const SHEET_WIN_H = systemInfo.windowHeight || 700
/**
 * 把手区高度（px）。它就是「不覆盖」档下露出的全部高度，也是手指的落点范围——
 * 手机上按 20 多 px 的细条按不准，所以这里给足高度，并且整个把手区都能拖。
 */
const SHEET_GRIP_H = 64
/**
 * 四档的「顶边位置」数组，按覆盖面积从多到少排列：
 * 下标越大，框越往下、覆盖越少。
 * 定位用 top（见下方 sheetStyle 注释），而不是 transform。
 */
const SHEET_STOPS = [
  Math.round(SHEET_WIN_H * 0.25), // 覆盖 3/4
  Math.round(SHEET_WIN_H * 0.5),  // 覆盖 1/2
  Math.round(SHEET_WIN_H * 0.75), // 覆盖 1/4
  SHEET_WIN_H - SHEET_GRIP_H      // 不覆盖：只留把手区
]
const SHEET_MIN_OFFSET = SHEET_STOPS[0]
const SHEET_MAX_OFFSET = SHEET_STOPS[SHEET_STOPS.length - 1]
/** 默认停在「覆盖 1/2」 */
const SHEET_DEFAULT_INDEX = 1

/** 当前停靠的档位下标 */
const sheetIndex = ref(SHEET_DEFAULT_INDEX)
/** 上拉框当前顶边位置（px），拖拽过程中实时变化、不一定落在档位上 */
const sheetOffset = ref(SHEET_STOPS[SHEET_DEFAULT_INDEX])
/** 拖拽过程中关闭过渡动画，跟手 */
const sheetDragging = ref(false)
/** 是否处于「不覆盖」档（只留把手，此时给一行提示） */
const sheetCollapsed = computed(() => sheetIndex.value === SHEET_STOPS.length - 1)

/**
 * 上拉框定位用 top 而不是 transform：各档下框的可见高度不同，而框底必须始终贴着屏幕底，
 * 所以只能让 top 跟着变；纯 transform 位移会让框底跑出屏幕、底部按钮不可见。
 * 缓动用 ease-out 曲线，收尾更「跟手」。
 */
const sheetStyle = computed(() => ({
  top: `${sheetOffset.value}px`,
  transition: sheetDragging.value ? 'none' : 'top 0.32s cubic-bezier(0.22, 1, 0.36, 1)'
}))

let dragStartY = 0
let dragStartOffset = 0
/** 本次触摸是否真的拖动过（用于区分「拖完松手」与「原地轻点」） */
let dragMoved = false
/** 最近一次移动的坐标与时间，用于估算甩动速度 */
let lastMoveY = 0
let lastMoveAt = 0
/** 甩动速度（px/ms，正数=向下） */
let dragVelocity = 0
/** 上次拖拽结束时间：拖完紧跟的 tap 不应再切换档位，否则会把刚拖到的位置弹回去 */
let lastDragEndAt = 0

/** 距给定位置最近的那一档下标 */
function nearestStopIndex(offset: number): number {
  let best = 0
  let bestDist = Number.POSITIVE_INFINITY
  SHEET_STOPS.forEach((stop, i) => {
    const d = Math.abs(stop - offset)
    if (d < bestDist) {
      bestDist = d
      best = i
    }
  })
  return best
}

function onSheetTouchStart(e: TouchEvent) {
  const t = e.touches?.[0]
  if (!t) return
  dragStartY = t.clientY
  dragStartOffset = sheetOffset.value
  lastMoveY = t.clientY
  lastMoveAt = Date.now()
  dragVelocity = 0
  dragMoved = false
  sheetDragging.value = true
}

function onSheetTouchMove(e: TouchEvent) {
  // 没有经过 touchstart 的游离 move 直接忽略，避免出现「移动了但松手不吸附」
  if (!sheetDragging.value) return
  const t = e.touches?.[0]
  if (!t) return

  const delta = t.clientY - dragStartY
  if (Math.abs(delta) > 8) dragMoved = true

  // 估算瞬时速度（px/ms），松手时用判断是否甩动
  const now = Date.now()
  const dt = now - lastMoveAt
  if (dt > 0) dragVelocity = (t.clientY - lastMoveY) / dt
  lastMoveY = t.clientY
  lastMoveAt = now

  // 夹在首尾两档之间，不允许拖出范围
  sheetOffset.value = Math.min(
    SHEET_MAX_OFFSET,
    Math.max(SHEET_MIN_OFFSET, dragStartOffset + delta)
  )
}

/** 松手：先吸附到最近档，甩得够快再顺方向多跨一档 */
function onSheetTouchEnd() {
  if (!sheetDragging.value) return
  sheetDragging.value = false
  lastDragEndAt = Date.now()

  let idx = nearestStopIndex(sheetOffset.value)
  const FLING_SPEED = 0.6 // px/ms
  if (dragVelocity > FLING_SPEED) idx += 1
  else if (dragVelocity < -FLING_SPEED) idx -= 1
  idx = Math.min(SHEET_STOPS.length - 1, Math.max(0, idx))

  sheetIndex.value = idx
  sheetOffset.value = SHEET_STOPS[idx]
}

/** 点把手：依次循环四档（拖动之外的第二条路径，手机上按不准也能操作） */
function cycleSheet() {
  if (Date.now() - lastDragEndAt < 250) return
  const next = (sheetIndex.value + 1) % SHEET_STOPS.length
  sheetIndex.value = next
  sheetOffset.value = SHEET_STOPS[next]
}

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
  const page = pages[pages.length - 1] as { options?: { id?: string; featuredId?: string } }
  const id = page.options?.id
  const featuredId = page.options?.featuredId

  if (featuredId) {
    // 精选行程预览：后端返回内嵌的完整行程，直接渲染；此页为只读，不显示任何编辑操作
    featuredTripId.value = featuredId
    try {
      const detail = await getFeaturedDetail(featuredId)
      trip.value = (detail?.trip as TripPlan) || null
      if (trip.value && detail?.title) trip.value.title = detail.title
    } catch (e) {
      console.warn('[trip/detail] 获取精选行程失败:', e)
    }
  } else if (!id) {
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

// 防误退：本页若成了 WebView 历史的第一条（如 App 冷启动恢复到行程详情），
// 手机边缘滑动返回会让 Capacitor 直接退出应用，这里兜住改为回首页。
let uninstallBackGuard: (() => void) | null = null
onMounted(() => {
  uninstallBackGuard = installBackGuard()
})
onUnmounted(() => {
  uninstallBackGuard?.()
  uninstallBackGuard = null
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
  uni.navigateBack({
    fail: () => {
      // 没有上一页（从外部链接/冷启动恢复直接进来）：回首页而不是停在空白页
      uni.switchTab({
        url: '/pages/home/index',
        fail: () => uni.reLaunch({ url: '/pages/home/index' })
      })
    }
  })
}

function onShare() {
  uni.showToast({ title: '分享功能开发中', icon: 'none' })
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

// ===== 精选行程预览（只读）的两个动作 =====

/** 添加到我的行程：后端把内嵌行程复制成一份归属当前用户的独立副本 */
async function onAddToMine() {
  if (!checkLogin()) return
  if (!featuredTripId.value) return
  try {
    await copyFeaturedToMine(featuredTripId.value)
    uni.showToast({ title: '已添加到我的行程', icon: 'success' })
    setTimeout(() => uni.switchTab({ url: '/pages/trip/index' }), 800)
  } catch (e) {
    // 失败提示由请求层统一弹出，这里只留日志
    console.warn('[trip/detail] 添加到我的行程失败:', e)
  }
}

/**
 * 组装给 AI 的行程上下文：带完整明细（时间段/景点/门票/开放时间/地址），
 * 这样对话里能改到具体时段、也能删掉某个具体景点。
 */
function buildItineraryContext(t: TripPlan): string {
  const lines: string[] = []
  lines.push(`参考行程：《${t.title || '未命名行程'}》`)
  const head = [t.toCity ? `目的地${t.toCity}` : '', t.days ? `${t.days}天` : '']
    .filter(Boolean)
    .join('、')
  if (head) lines.push(head)
  ;(t.dayPlans || []).forEach((day, di) => {
    const items = day.schedules || []
    if (!items.length) return
    lines.push(`第${di + 1}天：`)
    items.forEach((s) => {
      const parts = [s.time || '', s.title || ''].filter(Boolean).join(' ')
      const extra = [
        s.ticket ? `门票${s.ticket}` : '',
        s.openTime ? `开放${s.openTime}` : '',
        s.location ? `地址${s.location}` : ''
      ].filter(Boolean)
      lines.push(`- ${parts}${extra.length ? `（${extra.join('，')}）` : ''}`)
    })
  })
  lines.push('我会在上面的行程基础上提修改要求，请在保留整体结构的前提下按我的要求调整。')
  return lines.join('\n')
}

/**
 * 对话修改：把精选行程放进「当前行程」（尚无 id、未落库），带上完整明细作为对话上下文，
 * 跳到 AI 对话页继续聊。对话页在生成最终计划时会自动保存进我的行程，
 * 所以是「先对话改、满意再保存」。
 */
function onChatModify() {
  if (!checkLogin()) return
  const t = trip.value
  if (!t) return
  tripStore.currentTrip = {
    ...t,
    // 关键：清掉来源标识，这是尚未落库的草稿，保存时才会新建
    id: undefined,
    chatSessionId: undefined,
    contextNote: buildItineraryContext(t)
  }
  // 避免恢复上一次无关的会话历史
  clearActiveChatSession()
  uni.navigateTo({ url: '/pages/ai/chat' })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  // 地图铺满做背景层，页面本身不滚动（滚动交给上拉框内部的 scroll-view）
  background: var(--bg-muted);
}

/* 背景层：地图铺满整屏，导航与上拉框都是叠在它上面的浮层 */
.map-bg {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1;
}

/* 浮层操作按钮：返回在左，分享 / 设置（进编辑页）在右，都是毛玻璃圆钮 */
.float-actions {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  z-index: 300;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.icon-btn-group {
  display: flex;
  gap: 16rpx;
}

.icon-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--glass-bg);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.1);
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

/* 空状态：行程/路线数据不存在时的提示 */
/* 空状态：盖在地图之上，避免与地图自身的「暂无路线坐标」文案叠在一起 */
.empty-state {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 400;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  padding: 0 48rpx;
  background: var(--bg-page);
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

/* 行程摘要：浮在地图左上（毛玻璃胶囊，在浮层按钮行下方） */
.stats-bar {
  position: fixed;
  left: 24rpx;
  z-index: 250;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: var(--glass-bg);
  backdrop-filter: blur(20rpx);
  -webkit-backdrop-filter: blur(20rpx);
}

.stats-tag {
  font-size: 22rpx;
  color: $mint-primary;
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

/* 总览图例：各天颜色标识（放在上拉框内，避免被框遮住） */
.route-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx;
  padding-top: 16rpx;
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

/* ===== 上拉框：两档（半屏 ↔ 全屏）=====
   定位用 top（由内联样式给出）+ bottom:0，保证任何档位下框底都贴着屏幕底，
   底部操作按钮始终可见。 */
.sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 200;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
  border-radius: 32rpx 32rpx 0 0;
  box-shadow: 0 -8rpx 32rpx rgba(0, 0, 0, 0.12);
  overflow: hidden;
}

/* 把手区：整块都能拖（高度由内联样式给出，与 JS 的 SHEET_GRIP_H 同一个来源）。
   touch-action: none 是关键——告诉浏览器这块区域的触摸不参与滚动、下拉刷新等默认手势，
   否则 Android WebView 会把垂直拖拽当成页面滚动抢走，表现就是「拖不动」。 */
.sheet-grip {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  touch-action: none;
}

/* 把手横杠：必须明显可见——用户要能一眼看出这里可以拖。
   不能用 --border（#E8F5F2 与框底色 #F9FDFB 几乎同色，等于看不见）。 */
.handle-bar {
  width: 88rpx;
  height: 10rpx;
  border-radius: 999rpx;
  background: var(--text-tertiary);
}

.grip-hint {
  font-size: 20rpx;
  color: var(--text-tertiary);
}

.sheet-head {
  flex-shrink: 0;
  padding: 0 32rpx 16rpx;
}

/* 精选行程提示：说明来源与可用动作 */
.featured-tip {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding-bottom: 14rpx;
}

.featured-chip {
  flex-shrink: 0;
  font-size: 20rpx;
  color: #ffffff;
  background: #58a883;
  border-radius: 8rpx;
  padding: 4rpx 12rpx;
}

.featured-tip-text {
  font-size: 22rpx;
  color: var(--text-tertiary);
}

/* 行程名：原来在顶部导航栏，导航栏去掉后移到这里（卡片区上方） */
.sheet-title {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  font-size: 36rpx;
  font-weight: 700;
  line-height: 1.3;
  color: var(--text-main);
  padding-bottom: 20rpx;
}

.sheet-body {
  flex: 1;
  height: 0;
  padding: 8rpx 32rpx 0;
  box-sizing: border-box;
}

.sheet-footer {
  flex-shrink: 0;
  display: flex;
  gap: 20rpx;
  padding: 20rpx 32rpx;
  border-top: 1rpx solid var(--border);
  background: var(--bg-page);
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

.sheet-footer .btn-mint-outline {
  flex: 1;
}

.footer-main {
  flex: 2;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 28rpx;
}
</style>
