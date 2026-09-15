<template>
  <view class="trip-map" :style="{ height: height }">
    <!-- H5：腾讯地图 JS SDK（动态加载） -->
    <!-- #ifdef H5 -->
    <view :id="mapDomId" class="qqmap-container" />
    <view v-if="!mapReady" class="map-placeholder">
      <view class="map-ph-spinner" />
      <text class="map-ph-text">地图加载中…</text>
    </view>
    <!-- #endif -->

    <!-- App / 小程序：uni 原生 <map> 组件 -->
    <!-- #ifndef H5 -->
    <map
      :id="mapDomId"
      class="uni-map"
      :latitude="centerLat"
      :longitude="centerLng"
      :scale="scale"
      :markers="markers"
      :polyline="polyline"
      :include-points="includePoints"
      show-location
      @markertap="onMarkerTap"
    />
    <!-- #endif -->

    <!-- 无可绘制点的占位 -->
    <view v-if="!hasSpots" class="map-empty">
      <text class="map-empty-text">{{ emptyText }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
// #ifdef H5
import { loadQqMapSdk } from '@/utils/qqmap'
// #endif
import type { LatLng } from '@/utils/geo'

/**
 * 跨端行程路线地图组件
 * - H5：腾讯地图 JS SDK，动态加载脚本，用 Marker/Polyline 绘制
 * - App / 小程序：uni 原生 <map>，markers + polyline 数组
 * 支持两类用法：
 *   1) 传入 spots（单组，如 AI 生成遮罩"边生成边画"）：内部自动绘制并 fitBounds
 *   2) 传入 routeGroups（多组，如详情页按天分色 / 总览叠加）：
 *      每组独立颜色绘制，切换后调用 focus() 让视角跟随
 */
export interface TripMapSpot extends LatLng {
  /** 景点名/编号，用于标注气泡 */
  name?: string
  /** 详细地址（H5 可作为 title） */
  address?: string
}

/** 一组路线（同一天/同一颜色） */
export interface RouteGroup {
  /** 路线颜色（折线/标记） */
  color: string
  /** 组名，如"第1天"，用于 marker 气泡 */
  label?: string
  /** 该组景点（已含经纬度） */
  spots: TripMapSpot[]
  /** 驾车规划出的真实路径点（沿道路）；缺省时直线连接 spots */
  path?: LatLng[]
}

interface Props {
  /** 初始/全部景点（单组）；AI 生成遮罩用 appendSpot 逐点追加 */
  spots?: TripMapSpot[]
  /** 多组路线（详情页按天分色 / 总览）；与 spots 二选一，优先 routeGroups */
  routeGroups?: RouteGroup[]
  /** 默认中心点城市名（用于无坐标时的初始视野） */
  city?: string
  /** 地图高度，支持 rpx/px */
  height?: string
  /** 无点时占位文案 */
  emptyText?: string
}

/** uni 原生 <map> 标记点形状（仅包含绘制路线所需字段） */
interface MarkerLabel {
  content: string
  color: string
  bgColor: string
  borderRadius: number
  padding: number
  fontSize: number
}
interface MarkerShape {
  id: number
  latitude: number
  longitude: number
  title?: string
  label?: MarkerLabel
  width?: number
  height?: number
}
interface PointShape {
  latitude: number
  longitude: number
}
interface PolylineShape {
  points: PointShape[]
  color: string
  width: number
  arrowLine?: boolean
  borderWidth?: number
  borderColor?: string
}

/** 默认路线颜色（单组场景） */
const DEFAULT_COLOR = '#14b8a6'

const props = withDefaults(defineProps<Props>(), {
  spots: () => [],
  routeGroups: () => [],
  city: '',
  height: '420rpx',
  emptyText: '暂无路线坐标'
})

const emit = defineEmits<{
  (e: 'spotTap', spot: TripMapSpot, groupIndex: number, spotIndex: number): void
}>()

/** 内部已绘制的路线组（appendSpot 追加到第 0 组） */
const drawnRoutes = ref<RouteGroup[]>([])
const hasSpots = ref(false)

const mapDomId = `tripmap_${Math.random().toString(36).slice(2, 9)}`

// ===== App / 小程序原生 <map> 所需状态 =====
const centerLat = ref(39.908)
const centerLng = ref(116.397)
const scale = ref(12)
const markers = ref<MarkerShape[]>([])
const polyline = ref<PolylineShape[]>([])
const includePoints = ref<PointShape[]>([])

// ===== H5 腾讯地图 SDK 状态 =====
const mapReady = ref(false)
let qqMap: any = null
let qqMarkers: any[] = []
let qqPolylines: any[] = []

// #ifdef H5
/**
 * 数字圆点徽标：canvas 生成图片作为 marker 图标，标注行程顺序。
 * 腾讯地图 GL 的内置大头针无法渲染文字，图片是唯一稳妥的做法。
 * 2x 尺寸绘制（显示 26px），保证高分屏清晰。按 颜色#序号 缓存。
 */
const badgeCache = new Map<string, string>()
function numberBadgeSrc(color: string, num: number): string {
  const key = `${color}#${num}`
  const cached = badgeCache.get(key)
  if (cached) return cached

  const S = 52
  const canvas = document.createElement('canvas')
  canvas.width = S
  canvas.height = S
  const ctx = canvas.getContext('2d')
  ctx.beginPath()
  ctx.arc(S / 2, S / 2, S / 2 - 4, 0, Math.PI * 2)
  ctx.fillStyle = color
  ctx.fill()
  ctx.lineWidth = 4
  ctx.strokeStyle = '#ffffff'
  ctx.stroke()
  ctx.fillStyle = '#ffffff'
  ctx.font = 'bold 26px -apple-system, "PingFang SC", sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(String(num), S / 2, S / 2 + 2)

  const url = canvas.toDataURL('image/png')
  badgeCache.set(key, url)
  return url
}

interface NamePlate {
  src: string
  /** 显示尺寸（px） */
  w: number
  h: number
}

/**
 * 名称牌：白底 + 组色描边圆角牌，标注地点名。
 * 牌顶部画了一段透明间隙，让牌悬在数字圆点正下方而不重叠。按 颜色#名称 缓存。
 */
const plateCache = new Map<string, NamePlate>()
function namePlate(name: string, color: string): NamePlate {
  const key = `${color}#${name}`
  const cached = plateCache.get(key)
  if (cached) return cached

  const text = name.length > 12 ? `${name.slice(0, 11)}…` : name
  const GAP = 28 // 牌顶透明间隙（2x，显示 14px）
  const H = 40 // 牌高（2x，显示 20px）
  const FS = 22 // 字号（2x，显示 11px）
  const font = `500 ${FS}px "PingFang SC", "Microsoft YaHei", sans-serif`

  const measure = document.createElement('canvas').getContext('2d')
  measure.font = font
  const W = Math.ceil(measure.measureText(text).width) + 32

  const canvas = document.createElement('canvas')
  canvas.width = W
  canvas.height = GAP + H
  const ctx = canvas.getContext('2d')
  const r = 8
  ctx.beginPath()
  ctx.moveTo(r, GAP)
  ctx.arcTo(W - 1, GAP, W - 1, GAP + H - 1, r)
  ctx.arcTo(W - 1, GAP + H - 1, 1, GAP + H - 1, r)
  ctx.arcTo(1, GAP + H - 1, 1, GAP, r)
  ctx.arcTo(1, GAP, W - 1, GAP, r)
  ctx.closePath()
  ctx.fillStyle = 'rgba(255, 255, 255, 0.96)'
  ctx.fill()
  ctx.lineWidth = 3
  ctx.strokeStyle = color
  ctx.stroke()

  ctx.font = font
  ctx.fillStyle = '#2f3b3a'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(text, W / 2, GAP + H / 2 + 1)

  const plate: NamePlate = { src: canvas.toDataURL('image/png'), w: W / 2, h: (GAP + H) / 2 }
  plateCache.set(key, plate)
  return plate
}

/** 初始化 H5 地图实例 */
async function initH5Map() {
  try {
    const TMap = await loadQqMapSdk()
    await nextTick()
    if (!TMap) return
    const el = document.getElementById(mapDomId)
    if (!el) return
    qqMap = new TMap.Map(el, {
      center: new TMap.LatLng(centerLat.value, centerLng.value),
      zoom: 12
    })
    mapReady.value = true
    // 初次绘制已有路线
    redrawH5()
  } catch (err) {
    console.warn('[TripMap] H5 地图初始化失败:', err)
    mapReady.value = false
  }
}

/** H5：绘制全部路线组（标记 + 每组一条折线） */
function redrawH5() {
  const TMap = (window as any).TMap
  if (!qqMap || !TMap) return

  // 清理旧覆盖物
  qqMarkers.forEach((m) => m.setMap && m.setMap(null))
  qqMarkers = []
  qqPolylines.forEach((p) => p.setMap && p.setMap(null))
  qqPolylines = []

  const bounds = new TMap.LatLngBounds()
  let pointCount = 0

  drawnRoutes.value.forEach((group, gi) => {
    const color = group.color || DEFAULT_COLOR
    const path: any[] = []

    group.spots.forEach((s, si) => {
      const ll = new TMap.LatLng(s.lat, s.lng)
      path.push(ll)
      bounds.extend(ll)
      pointCount++

      // 数字圆点：标注行程顺序（组内序号；总览时即当天内的先后）
      const num = si + 1
      const marker = new TMap.MultiMarker({
        map: qqMap,
        styles: {
          default: new TMap.MarkerStyle({
            width: 26,
            height: 26,
            anchor: { x: 13, y: 13 },
            src: numberBadgeSrc(color, num)
          })
        },
        geometries: [
          {
            id: `g${gi}_s${si}`,
            position: ll,
            properties: { spot: s, groupIndex: gi, spotIndex: si }
          }
        ]
      })
      marker.on('click', () => emit('spotTap', s, gi, si))
      qqMarkers.push(marker)

      // 名称牌：标注地点名，悬在数字圆点正下方
      if (s.name) {
        const plate = namePlate(s.name, color)
        const plateMarker = new TMap.MultiMarker({
          map: qqMap,
          styles: {
            default: new TMap.MarkerStyle({
              width: plate.w,
              height: plate.h,
              anchor: { x: plate.w / 2, y: 0 },
              src: plate.src
            })
          },
          geometries: [{ id: `lbl_g${gi}_s${si}`, position: ll }]
        })
        qqMarkers.push(plateMarker)
      }
    })

    // 每组一条折线（>=2 点才画线）：优先使用驾车规划的真实路径，缺省直线连接
    const routePath =
      group.path && group.path.length >= 2
        ? group.path.map((p) => new TMap.LatLng(p.lat, p.lng))
        : path
    if (routePath.length >= 2) {
      const pl = new TMap.MultiPolyline({
        map: qqMap,
        styles: {
          default: new TMap.PolylineStyle({
            color,
            width: 6,
            borderWidth: 2,
            borderColor: '#ffffff',
            lineCap: 'round'
          })
        },
        geometries: [{ id: `route_${gi}`, paths: routePath }]
      })
      qqPolylines.push(pl)
    }
  })

  // 自适应视野（初始绘制/切换天时跟随）
  if (pointCount > 1) {
    qqMap.fitBounds(bounds, { top: 40, bottom: 40, left: 40, right: 40 })
  } else if (pointCount === 1) {
    const g = drawnRoutes.value.find((x) => x.spots.length)
    const s = g?.spots[0]
    if (s) qqMap.setCenter(new TMap.LatLng(s.lat, s.lng))
  }
}

/** H5：聚焦到当前全部路线点（切换天/总览后调用） */
function focusH5() {
  const TMap = (window as any).TMap
  if (!qqMap || !TMap) return
  const bounds = new TMap.LatLngBounds()
  let n = 0
  drawnRoutes.value.forEach((g) =>
    g.spots.forEach((s) => {
      bounds.extend(new TMap.LatLng(s.lat, s.lng))
      n++
    })
  )
  if (n > 1) {
    qqMap.fitBounds(bounds, { top: 60, bottom: 60, left: 60, right: 60 })
  } else if (n === 1) {
    const s = drawnRoutes.value.find((g) => g.spots.length)?.spots[0]
    if (s) qqMap.setCenter(new TMap.LatLng(s.lat, s.lng))
  }
}
// #endif

// ===== App / 小程序原生 <map> 绘制 =====
// #ifndef H5
function redrawNative() {
  const all: MarkerShape[] = []
  const lines: PolylineShape[] = []
  const points: PointShape[] = []
  let markerId = 0
  let total = 0

  drawnRoutes.value.forEach((group) => {
    const color = group.color || DEFAULT_COLOR
    const spots = group.spots
    total += spots.length
    spots.forEach((s, si) => {
      all.push({
        id: markerId++,
        latitude: s.lat,
        longitude: s.lng,
        title: `${group.label || ''}${si + 1} ${s.name || ''}`,
        // 原生 <map> 的 label 只支持一段文字，这里把「顺序序号 + 地点名」合成一段
        label: {
          content: s.name ? `${si + 1} ${s.name}` : `${si + 1}`,
          color: '#ffffff',
          bgColor: color,
          borderRadius: 12,
          padding: 4,
          fontSize: 12
        },
        width: 28,
        height: 36
      })
      points.push({ latitude: s.lat, longitude: s.lng })
    })
    if (spots.length >= 2) {
      const pts = group.path && group.path.length >= 2 ? group.path : spots
      lines.push({
        points: pts.map((s) => ({ latitude: s.lat, longitude: s.lng })),
        color: color + 'FF',
        width: 6,
        arrowLine: true,
        borderWidth: 2,
        borderColor: '#ffffffFF'
      })
    }
  })

  hasSpots.value = total > 0
  markers.value = all
  polyline.value = lines
  includePoints.value = points
  if (points.length) {
    centerLat.value = points[0].latitude
    centerLng.value = points[0].longitude
    scale.value = total > 1 ? 12 : 14
  }
}

function onMarkerTap(e: any) {
  const idx = e.markerId ?? e.detail?.markerId
  if (typeof idx === 'number') {
    // 原生端 markerId 即全局序号，反查组/组内序号
    let gi = 0
    let si = 0
    let remain = idx
    for (let i = 0; i < drawnRoutes.value.length; i++) {
      const len = drawnRoutes.value[i].spots.length
      if (remain < len) {
        gi = i
        si = remain
        break
      }
      remain -= len
    }
    const spot = drawnRoutes.value[gi]?.spots[si]
    if (spot) emit('spotTap', spot, gi, si)
  }
}
// #endif

/** 追加一个景点到第 0 组并立即绘制（"边生成边画"动画用） */
function appendSpot(spot: TripMapSpot) {
  if (!drawnRoutes.value.length) {
    drawnRoutes.value = [{ color: DEFAULT_COLOR, spots: [] }]
  }
  drawnRoutes.value[0].spots.push(spot)
  hasSpots.value = drawnRoutes.value.some((g) => g.spots.length)
  // #ifdef H5
  redrawH5()
  // #endif
  // #ifndef H5
  redrawNative()
  // #endif
}

/** 重置为单组路线并重绘 */
function setSpots(spots: TripMapSpot[]) {
  drawnRoutes.value = [{ color: DEFAULT_COLOR, spots: [...spots] }]
  hasSpots.value = drawnRoutes.value.some((g) => g.spots.length)
  // #ifdef H5
  redrawH5()
  // #endif
  // #ifndef H5
  redrawNative()
  // #endif
}

/** 重置为多组路线并重绘（详情页按天分色 / 总览） */
function setRoutes(groups: RouteGroup[]) {
  drawnRoutes.value = groups.map((g) => ({
    color: g.color || DEFAULT_COLOR,
    label: g.label,
    spots: [...g.spots],
    path: g.path ? [...g.path] : undefined
  }))
  hasSpots.value = drawnRoutes.value.some((g) => g.spots.length)
  // #ifdef H5
  redrawH5()
  // #endif
  // #ifndef H5
  redrawNative()
  // #endif
}

/** 清空地图 */
function clearSpots() {
  drawnRoutes.value = []
  hasSpots.value = false
  // #ifdef H5
  redrawH5()
  // #endif
  // #ifndef H5
  redrawNative()
  // #endif
}

/** 获取第 0 组已绘制的景点（含经纬度；用于驾车路线规划） */
function getSpots(): TripMapSpot[] {
  return drawnRoutes.value[0]?.spots || []
}

/** 视角聚焦到当前全部路线点（切换天/总览后调用） */
function focus() {
  // #ifdef H5
  focusH5()
  // #endif
  // #ifndef H5
  redrawNative()
  // #endif
}

// 暴露方法供父组件调用
defineExpose({ appendSpot, setSpots, setRoutes, clearSpots, focus, getSpots })

// spots prop 变化时同步（单组场景）
watch(
  () => props.spots,
  (newSpots) => {
    setSpots(newSpots)
  },
  { deep: true }
)

// routeGroups prop 变化时同步（详情页切换天/总览）
watch(
  () => props.routeGroups,
  (newGroups) => {
    setRoutes(newGroups)
  },
  { deep: true }
)

onMounted(() => {
  // 初始路线：优先多组（保留驾车路径）
  if (props.routeGroups?.length) {
    drawnRoutes.value = props.routeGroups.map((g) => ({
      color: g.color || DEFAULT_COLOR,
      label: g.label,
      spots: [...g.spots],
      path: g.path ? [...g.path] : undefined
    }))
  } else {
    drawnRoutes.value = [{ color: DEFAULT_COLOR, spots: [...props.spots] }]
  }
  hasSpots.value = drawnRoutes.value.some((g) => g.spots.length)
  // #ifdef H5
  initH5Map()
  // #endif
  // #ifndef H5
  redrawNative()
  // #endif
})

onBeforeUnmount(() => {
  // #ifdef H5
  if (qqMap && qqMap.destroy) qqMap.destroy()
  qqMap = null
  qqMarkers = []
  qqPolylines = []
  // #endif
})
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.trip-map {
  position: relative;
  width: 100%;
  border-radius: $card-radius-lg;
  overflow: hidden;
  background: var(--bg-input);
}

/* H5 腾讯地图容器 */
.qqmap-container {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;

  /* 腾讯地图 GL 控件定制（SDK 未提供关闭/尺寸选项，用 CSS 处理） */
  /* 去掉右上角指南针/旋转控件 */
  :deep(.rotate-circle) {
    display: none !important;
  }
  /* 右上角缩放按钮（+/-）：隐藏（缩放交给双指捏合 / 滚轮手势） */
  :deep(.tmap-zoom-control) {
    display: none !important;
  }
}

/* App/小程序 uni 原生 map */
.uni-map {
  width: 100%;
  height: 100%;
}

/* 加载占位 */
.map-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  gap: 16rpx;
}

.map-ph-spinner {
  width: 48rpx;
  height: 48rpx;
  border: 4rpx solid var(--border);
  border-top-color: $mint-primary;
  border-radius: 50%;
  animation: tripmap-spin 0.8s linear infinite;
}

@keyframes tripmap-spin {
  to {
    transform: rotate(360deg);
  }
}

.map-ph-text {
  font-size: 24rpx;
  color: var(--text-secondary);
}

/* 空态占位 */
.map-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-input);
}

.map-empty-text {
  font-size: 24rpx;
  color: var(--text-tertiary);
}
</style>