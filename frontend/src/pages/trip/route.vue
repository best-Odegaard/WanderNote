<template>
  <view class="page">
    <view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="nav-inner" :style="{ height: navHeight + 'px' }">
        <view class="back" @tap="goBack">
          <AppIcon name="chevron-left" :size="34" color="var(--text-body)" />
        </view>
        <text class="nav-title">今日路线</text>
        <text class="nav-map" @tap="onSwitchProvider">{{ providerLabel }} ›</text>
      </view>
    </view>

    <view class="map-wrap">
      <TripMap v-if="!loading && hasRoute" ref="mapRef" :route-groups="mapGroups" :city="city" height="100%" />
      <view v-else class="map-placeholder">
        <text class="ph-text">{{ emptyText }}</text>
      </view>
    </view>

    <view class="sheet">
      <view class="sheet-handle" />

      <view v-if="hasRoute" class="summary">
        <text class="summary-main">
          {{ dayLabel }} · {{ nodes.length }} 个点 · {{ formatDistance(totalKm) }} · 驾车约 {{ formatDuration(totalMin) }}
        </text>
        <!--
          住宿：分「没有住宿记录」「有名字没坐标」「名字与已选不一致」三种说明。
          原来只要闭环不成立就说「还没有住宿信息」，可住宿卡上明明写着酒店名，
          用户按提示重选也未必能解决（P2-11）。
        -->
        <text v-if="!closed" class="summary-tip">{{ hotelTip }}</text>
        <text v-if="!closed && hotelState !== 'none'" class="summary-tip link" @tap="onRechooseHotel">
          重新选择住宿 ›
        </text>
        <text v-if="dropped.length" class="summary-tip">
          有 {{ dropped.length }} 个地点没定位到，已从路线里跳过：{{ dropped.join('、') }}
        </text>
        <text v-if="noMapKey" class="summary-tip warn">
          未配置腾讯地图 Key（VITE_MAP_WS_KEY），无法定位与规划真实路线
        </text>
      </view>

      <scroll-view scroll-y class="body">
        <view v-for="(node, i) in nodes" :key="i" class="row">
          <view class="node" :class="{ 'is-hotel': node.kind === 'hotel' }">
            <text class="node-ico">{{ nodeIcon(node) }}</text>
            <view class="node-info">
              <text class="node-title">{{ node.title }}</text>
              <text v-if="node.time" class="node-time">{{ node.time }}</text>
              <text v-else-if="node.address" class="node-time">{{ node.address }}</text>
            </view>
          </view>

          <!-- 段：点一下选中为「当前段」，底部导航按钮就按它导航 -->
          <view
            v-if="i < legs.length"
            class="leg"
            :class="{ active: currentLegIndex === i }"
            @tap="selectLeg(i)"
          >
            <view class="leg-line" />
            <!--
              该段规划失败：原来只显示「驾车 — · —」，用户不知道是没数据还是坏了，
              也没有替代动作（P2-12）。这里说清原因，并指向下面的「导航这一段」兜底。
            -->
            <text v-if="legs[i]" class="leg-text">
              驾车 {{ formatDuration(legDuration(i)) }} · {{ formatDistance(legDistance(i)) }}
            </text>
            <text v-else class="leg-text warn">
              这一段没能规划出路线，可点下方「导航这一段」直接唤起外部地图
            </text>
            <text v-if="currentLegIndex === i" class="leg-flag">当前段</text>
          </view>
        </view>

        <!--
          无路线时的空态：必须区分「行程没加载出来」和「当天确实没安排」。
          原来无论哪种情况都只说「这一天还没有可导航的行程节点」，用户被误导为今天没行程，
          也没有重试入口（P1-23）。
        -->
        <view v-if="!loading && !hasRoute" class="empty">
          <text class="empty-text">{{ emptyText }}</text>
          <view v-if="canRetry" class="retry-btn" @tap="retry">重试</view>
        </view>
        <view style="height: 40rpx" />
      </scroll-view>

      <view v-if="hasRoute" class="footer safe-bottom">
        <view class="seg-row">
          <view class="seg-chip" :class="{ disabled: currentLegIndex <= 0 }" @tap="prevLeg">上一段</view>
          <view
            class="seg-chip"
            :class="{ disabled: currentLegIndex >= legs.length - 1 }"
            @tap="nextLeg"
          >下一段</view>
          <view class="seg-chip" @tap="onCopyAll">复制全部地址</view>
        </view>
        <button class="btn-main" :class="{ fallback: !currentLegPlanned }" @tap="onNavigateLeg">
          {{ navButtonLabel }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 今日路线：把某一天的行程变成「一条能照着走的闭环路线」。
 *
 * 与行程详情页的分工：
 *   · 详情页 = 看与改（地图 + 卡片 + 编辑）
 *   · 本页   = 照着走（分段耗时 / 接续导航 / 一键唤起地图）
 *
 * 关于「一次把全天导入高德」：做不到。高德 URI 的 via 最多 1 个途经点且仅驾车模式，
 * 所以这里走的是「应用内画完整路线 + 逐段唤起外部地图」的方案。
 */
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import TripMap from '@/components/TripMap/TripMap.vue'
import type { RouteGroup } from '@/components/TripMap/TripMap.vue'
import AppIcon from '@/components/AppIcon/AppIcon.vue'
import { useTripStore } from '@/store/trip'
import type { TripPlan } from '@/api/trip'
import { MAP_WS_KEY } from '@/utils/constant'
import type { LatLng } from '@/utils/geo'
import { installBackGuard } from '@/utils/backGuard'
import {
  buildDayRoute,
  formatDistance,
  formatDuration,
  type DayRoute,
  type RouteHotel,
  type RouteNode
} from '@/utils/routeBuild'
import {
  chooseNaviProvider,
  getPreferredProvider,
  openRouteNavi,
  NAVI_PROVIDER_LABEL,
  type NaviPoint,
  type NaviProvider
} from '@/utils/map'
import { showToast } from '@/utils/feedback'

const tripStore = useTripStore()

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const navHeight = statusBarHeight + 44

const mapRef = ref<InstanceType<typeof TripMap> | null>(null)
const loading = ref(true)
const dayIndex = ref(0)
const city = ref('')
const nodes = ref<RouteNode[]>([])
const legs = ref<DayRoute['legs']>([])
const totalKm = ref(0)
const totalMin = ref(0)
const closed = ref(false)
const dropped = ref<string[]>([])
const currentLegIndex = ref(0)
const provider = ref<NaviProvider>(getPreferredProvider())
/** 页面参数里的行程 id：重试时要按它重新拉一次详情 */
const tripId = ref('')
/** 带 id 却拉不到详情：不能用 store 里残留的别的行程兜底，标记为失败态（P1-23） */
const loadError = ref(false)
/** 首次构建是否结束：onShow 在首次进入时也会触发，用它区分「回到本页」与「刚进来」 */
const ready = ref(false)

/** 未配置地图 Key 时给出明确指引，而不是留一个空页面让人猜 */
const noMapKey = ref(!MAP_WS_KEY)

/**
 * 空态原因。原实现只有一句「这一天还没有可导航的行程节点」，
 * 但真实原因可能是「行程压根没加载出来」——用户被误导为今天没行程，也没有重试入口（P1-23）。
 */
type EmptyKind = 'loading' | 'error' | 'no-trip' | 'no-node' | 'no-locate'
const emptyKind = ref<EmptyKind>('loading')

const hasRoute = computed(() => nodes.value.length >= 2)
const providerLabel = computed(() => NAVI_PROVIDER_LABEL[provider.value])
const dayLabel = computed(() => `第 ${dayIndex.value + 1} 天`)
const emptyText = computed(() => {
  switch (emptyKind.value) {
    case 'loading':
      return '正在规划路线…'
    case 'error':
      return '行程加载失败，请检查网络后重试'
    case 'no-trip':
      return '没有可用的行程数据，请先从「我的行程」里打开一条行程'
    case 'no-locate':
      return '当天的地点都没能定位到（可能是未配置地图 Key 或网络不通）'
    default:
      return '这一天还没有可导航的行程节点'
  }
})
/** 只有「可能靠重试解决」的原因才给重试按钮，当天确实没安排时重试没有意义 */
const canRetry = computed(
  () => emptyKind.value === 'error' || emptyKind.value === 'no-trip' || emptyKind.value === 'no-locate'
)

/** 当前段是否规划成功：失败时要点明「导航这一段」是兜底路径（P2-12） */
const currentLegPlanned = computed(() => !!legs.value[currentLegIndex.value])
const navButtonLabel = computed(() => {
  const total = legs.value.length
  const idx = Math.min(currentLegIndex.value + 1, total)
  // 没有规划结果时不再报「第 x/y 段」，直接说明改用外部地图导航这一段
  return currentLegPlanned.value ? `🧭 导航第 ${idx}/${total} 段` : '🧭 导航这一段（改用外部地图）'
})

const mapGroups = computed<RouteGroup[]>(() => {
  const spots = nodes.value.map((n) => ({
    name: n.title,
    address: n.address,
    lat: n.lat,
    lng: n.lng
  }))
  // 各段路径首尾相接拼成全天路线；某段失败时留空，由 TripMap 用直线补齐观感
  const path: LatLng[] = []
  legs.value.forEach((leg) => {
    if (leg?.path?.length) path.push(...leg.path)
  })
  return [
    {
      color: '#14b8a6',
      label: '',
      spots,
      path: path.length > 1 ? path : undefined
    }
  ]
})

/**
 * 已用于构建路线的住宿标识。回到本页时用它判断住宿是否被改过，
 * 没变就不重复规划（否则每次 onShow 都要重跑一遍地理编码 + 分段规划）。
 */
let builtHotelKey = ''

function hotelKey(): string {
  const picked = tripStore.selectedHotel
  return picked ? `${picked.name}|${picked.lat}|${picked.lng}` : ''
}

/**
 * 住宿状态（P2-11）：区分「没有住宿记录」「有名字没坐标」「名字与已选不一致」。
 * 三种情况都表现为闭环不成立，但用户该做的事完全不同 —— 统一说「还没有住宿信息」
 * 会让住宿卡上明明写着酒店名的人一头雾水。
 */
const hotelState = computed<'none' | 'no-coord' | 'mismatch'>(() => {
  const trip = tripStore.currentTrip
  const picked = tripStore.selectedHotel
  const name = trip?.hotel || ''
  if (name && picked?.name && name !== picked.name) return 'mismatch'
  // 只有名字（没读到坐标）或选中项坐标为 0：都属于「有名字无坐标」
  if (picked && (!picked.lat || !picked.lng)) return 'no-coord'
  if (name && !picked) return 'no-coord'
  return 'none'
})

const hotelTip = computed(() => {
  const trip = tripStore.currentTrip
  const picked = tripStore.selectedHotel
  if (hotelState.value === 'mismatch') {
    return `行程里记录的住宿是「${trip?.hotel}」，与当前选中的「${picked?.name}」不是同一家，因此没有按它规划闭环`
  }
  if (hotelState.value === 'no-coord') {
    const name = picked?.name || trip?.hotel || '住宿'
    return `行程里记录了住宿「${name}」，但缺少它的坐标，无法规划「酒店出发 → 返回酒店」的闭环`
  }
  return '还没有住宿信息：当前路线是「第一站 → 最后一站」，设置酒店后才会变成「酒店出发 → 返回酒店」的闭环'
})

onLoad(async (options?: Record<string, string>) => {
  dayIndex.value = Math.max(0, Number(options?.day ?? 0) || 0)
  tripId.value = options?.id ? String(options.id) : ''
  await loadTrip(tripId.value)
  await buildRoute()
  ready.value = true
})

/**
 * 回到本页时重算路线。
 *
 * 场景：从这里点「重新选择住宿」去酒店页，选完 navigateBack 回来 —— 本页不会重挂载，
 * 不重算就还是「第一站 → 最后一站」的旧路线（P2-11）。
 * 只在住宿确实变了时才重跑，避免每次切走再回来都重新请求一遍地图接口。
 */
onShow(() => {
  if (!ready.value || loading.value) return
  if (hotelKey() === builtHotelKey) return
  retry()
})

/** 防误退（与详情页同一套兜底）：本页也可能成为 WebView 历史的第一条，见 utils/backGuard.ts */
let uninstallBackGuard: (() => void) | null = null
onMounted(() => {
  uninstallBackGuard = installBackGuard()
})
onUnmounted(() => {
  uninstallBackGuard?.()
  uninstallBackGuard = null
})

async function loadTrip(id: string) {
  if (id) {
    try {
      await tripStore.getTripDetail(id)
    } catch (e) {
      console.warn('[trip/route] 行程加载失败:', e)
      // 带 id 进来却拉不到：store 里可能还留着上一次打开的行程，
      // 拿它兜底会画出别人的路线，比空态更糟。记失败态，让用户自己重试（P1-23）。
      loadError.value = true
      return
    }
  }
  // 恢复住宿：直接从行程进路线页（没经过详情页）时，内存里没有选中的酒店。
  // 行程有住宿名但 store 里没有对应坐标，就去 trip_hotel 读一次 ——
  // 否则闭环会悄悄退化成「第一站 → 最后一站」，用户看到的路线少了一段。
  const trip = tripStore.currentTrip
  if (trip?.id && trip.hotel) {
    const picked = tripStore.selectedHotel
    if (!picked || picked.name !== trip.hotel || !picked.lat || !picked.lng) {
      await tripStore.loadTripHotel(trip.id)
    }
  }
}

/** 重试：重新拉一次行程详情并重算路线（P1-23 里给用户的出口） */
async function retry() {
  if (loading.value) return
  loading.value = true
  emptyKind.value = 'loading'
  loadError.value = false
  await loadTrip(tripId.value)
  await buildRoute()
}

/** 去酒店选择页重选住宿；回来时 onShow 会按新的选择重算闭环（P2-11） */
function onRechooseHotel() {
  const trip = tripStore.currentTrip
  const params = [
    `city=${encodeURIComponent(trip?.toCity || city.value || '')}`,
    `level=${encodeURIComponent(tripStore.hotelPreference || '无要求')}`,
    // from=detail：酒店页据此选择 navigateBack，直接回到本页而不是 push 对话页
    'from=detail'
  ]
  uni.navigateTo({ url: `/pages/plan/hotel?${params.join('&')}` })
}

/** 建路线：定位 → 闭环补点 → 分段规划（大部分时间花在这里） */
async function buildRoute() {
  loading.value = true
  emptyKind.value = 'loading'
  const trip = tripStore.currentTrip
  if (loadError.value) {
    // 带 id 却加载失败：先说清是加载失败，而不是「今天没安排」（P1-23）
    emptyKind.value = 'error'
    loading.value = false
    return
  }
  if (!trip?.dayPlans?.length) {
    // 前置条件不满足（冷启动直接进本页、或详情页没能写回 store）：这是「没有行程数据」
    emptyKind.value = 'no-trip'
    loading.value = false
    return
  }
  const dayPlan = trip.dayPlans[Math.min(dayIndex.value, trip.dayPlans.length - 1)]
  city.value = trip.toCity || ''
  const scheduleCount = dayPlan?.schedules?.length || 0

  // 住宿：酒店选择页选中的那家（见 resolveHotel）。有它才闭环成「酒店 → 各站 → 酒店」
  const hotel = resolveHotel(trip)

  try {
    const route = await buildDayRoute(dayPlan?.schedules || [], city.value, hotel)
    nodes.value = route.nodes
    legs.value = route.legs
    totalKm.value = route.totalKm
    totalMin.value = route.totalMin
    closed.value = route.closed
    dropped.value = route.dropped
    currentLegIndex.value = 0
    // 空态原因要按「当天到底有没有安排」来定：
    // 有安排但一个点都没定位到 → 是定位问题（可重试）；本来就没安排 → 是当天没节点。
    if (!scheduleCount) emptyKind.value = 'no-node'
    else if (route.nodes.length < 2) emptyKind.value = 'no-locate'
    builtHotelKey = hotelKey()
  } catch (e) {
    console.warn('[trip/route] 路线构建失败:', e)
    emptyKind.value = 'error'
    showToast({ title: '路线规划失败，请检查网络', icon: 'none' })
  } finally {
    loading.value = false
  }
}

/**
 * 从行程里解析住宿。
 *
 * 名称取 trip.hotel（后端 TripPlan 本来就有这个字段，酒店选择页会把选中的酒店名写进去），
 * 坐标取酒店选择页选中的那家店 —— utils/hotels.ts 的候选自带经纬度，所以不必再地理编码。
 *
 * 只有「行程里的名字」与「本次选中的酒店」一致时才闭环：
 *   行程名和选中店对不上（比如打开的是别人的旧行程）就返回 null，
 *   宁可退回「第一站 → 最后一站」，也不要按另一家酒店的坐标画出一条错路线。
 * 兜底候选（按城市临时生成的通用酒店）坐标是 0，同样返回 null。
 */
function resolveHotel(trip: TripPlan | null): RouteHotel | null {
  const picked = tripStore.selectedHotel
  if (!picked) return null
  if (trip?.hotel && trip.hotel !== picked.name) return null
  if (!picked.lat || !picked.lng) return null
  return { name: picked.name, lat: picked.lat, lng: picked.lng, address: picked.address }
}

function nodeIcon(node: RouteNode): string {
  if (node.kind === 'hotel') return '🏨'
  if (node.kind === 'food') return '🍜'
  return '📍'
}

function legDuration(i: number): number | null {
  return legs.value[i]?.durationMin ?? null
}

function legDistance(i: number): number | null {
  return legs.value[i]?.distanceKm ?? null
}

function selectLeg(i: number) {
  currentLegIndex.value = i
}

function prevLeg() {
  if (currentLegIndex.value > 0) currentLegIndex.value--
}

function nextLeg() {
  if (currentLegIndex.value < legs.value.length - 1) currentLegIndex.value++
}

function toNaviPoint(node: RouteNode): NaviPoint {
  return { name: node.title, lat: node.lat, lng: node.lng, address: node.address }
}

/** 导航当前段：起终点都用坐标，避免外部地图按名称搜索落到别的同名地点 */
function onNavigateLeg() {
  const i = currentLegIndex.value
  const from = nodes.value[i]
  const to = nodes.value[i + 1]
  if (!from || !to) {
    showToast({ title: '这一段还没有路线数据', icon: 'none' })
    return
  }
  openRouteNavi(provider.value, toNaviPoint(from), toNaviPoint(to))
}

async function onSwitchProvider() {
  const picked = await chooseNaviProvider()
  if (picked) {
    provider.value = picked
    showToast({ title: `已切换到${NAVI_PROVIDER_LABEL[picked]}`, icon: 'none' })
  }
}

/** 复制全天地址清单：给用户一个「换任何地图都能自己粘」的兜底 */
function onCopyAll() {
  const text = nodes.value
    .map((n, i) => `${i + 1}. ${n.title}${n.address ? ` ${n.address}` : ''}`)
    .join('\n')
  uni.setClipboardData({
    data: text,
    success: () => showToast({ title: '全天地址已复制', icon: 'success' })
  })
}

function goBack() {
  // 原来是无兜底的裸 navigateBack：冷启动 / 外链直达本页时没有上一页，
  // 点返回毫无反应（P2-13）。退回首页而不是停在原地。
  uni.navigateBack({
    fail: () => {
      uni.switchTab({
        url: '/pages/home/index',
        fail: () => uni.reLaunch({ url: '/pages/home/index' })
      })
    }
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--bg-page);
}

.nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 20;
  background: var(--bg-card);
  border-bottom: 1rpx solid var(--border);
}

.nav-inner {
  display: flex;
  align-items: center;
  padding: 0 24rpx;
}

.back {
  display: flex;
  align-items: center;
  margin-right: 12rpx;
  padding: 8rpx;
}

.nav-title {
  flex: 1;
  font-size: var(--fs-subhead);
  font-weight: 700;
  color: var(--text-main);
}

.nav-map {
  font-size: var(--fs-meta);
  color: var(--text-secondary);
  padding: 8rpx 0;
}

.map-wrap {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 46vh;
  background: var(--bg-muted);
}

.map-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ph-text {
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
}

.sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  top: calc(46vh - 40rpx);
  background: var(--bg-card);
  border-radius: 32rpx 32rpx 0 0;
  box-shadow: 0 -8rpx 32rpx rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sheet-handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: var(--border);
  margin: 16rpx auto 8rpx;
  flex: 0 0 auto;
}

.summary {
  padding: 8rpx 32rpx 16rpx;
  border-bottom: 1rpx solid var(--border);
  flex: 0 0 auto;
}

.summary-main {
  display: block;
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--text-main);
}

.summary-tip {
  display: block;
  margin-top: 8rpx;
  font-size: var(--fs-meta);
  line-height: 1.5;
  color: var(--text-tertiary);

  &.warn {
    color: var(--danger, #ef4444);
  }

  /* 可点的说明（如「重新选择住宿」）：用主色和 › 表明这里能操作 */
  &.link {
    color: var(--primary-strong, #4a9ef5);
  }
}

.body {
  flex: 1;
  min-height: 0;
  padding: 20rpx 32rpx 0;
}

.row {
  position: relative;
}

.node {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 0;
}

.node-ico {
  font-size: 34rpx;
  width: 44rpx;
  text-align: center;
}

.node-info {
  flex: 1;
  min-width: 0;
}

.node-title {
  display: block;
  font-size: var(--fs-body);
  color: var(--text-main);
}

.node-time {
  display: block;
  margin-top: 4rpx;
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
}

/* 一段路：可点击选中为当前段 */
.leg {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-left: 22rpx;
  padding: 10rpx 16rpx;
  border-radius: 12rpx;
  border-left: 4rpx solid var(--border);

  &.active {
    background: var(--primary-soft, rgba(74, 158, 245, 0.12));
    border-left-color: var(--primary-strong, #4a9ef5);
  }
}

.leg-text {
  font-size: var(--fs-meta);
  color: var(--text-secondary);
}

/* 该段没规划出路线：用警示色说清原因，而不是显示「—」 */
.leg-text.warn {
  flex: 1;
  color: var(--danger, #ef4444);
  line-height: 1.5;
}

.leg-flag {
  font-size: var(--fs-meta);
  color: var(--primary-strong, #4a9ef5);
  font-weight: 600;
}

.empty {
  padding: 60rpx 0;
  text-align: center;
}

.empty-text {
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
}

/* 空态的「重试」：失败/无数据时给一条自助出口，不必退出去重进（P1-23） */
.retry-btn {
  display: inline-block;
  margin-top: 24rpx;
  padding: 12rpx 48rpx;
  border-radius: 999rpx;
  border: 2rpx solid var(--primary-strong, #4a9ef5);
  color: var(--primary-strong, #4a9ef5);
  font-size: var(--fs-meta);
}

.footer {
  flex: 0 0 auto;
  padding: 16rpx 32rpx calc(16rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid var(--border);
}

.seg-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.seg-chip {
  flex: 1;
  text-align: center;
  padding: 14rpx 0;
  border-radius: 12rpx;
  background: var(--bg-muted);
  font-size: var(--fs-meta);
  color: var(--text-secondary);

  &.disabled {
    opacity: 0.4;
  }
}

.btn-main {
  width: 100%;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 999rpx;
  background: var(--primary-strong, #4a9ef5);
  color: #fff;
  font-size: var(--fs-title);
  font-weight: 600;
  border: none;

  &::after {
    border: none;
  }

  /* 当前段没规划出路线时按钮是唯一兜底路径：加描边让它更醒目（P2-12） */
  &.fallback {
    box-shadow: 0 0 0 4rpx rgba(74, 158, 245, 0.28);
  }
}
</style>
