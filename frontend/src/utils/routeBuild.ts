/**
 * 全天闭环路线的构建。
 *
 * 目标：把「某一天的行程卡片」变成「酒店出发 → 各站 → 返回酒店」的可导航路线，
 * 并给出每一段的驾车耗时与距离，供接续导航使用。
 *
 * 为什么要单独一层：行程数据里存的是「时间 + 标题 + 地址」，
 * 而导航要的是「有序坐标 + 分段路径」。这中间的定位、补点、闭环、分段规划
 * 不属于任何单个页面，放在这里让详情页/路线页共用。
 */
import { geocodeNodes, planRouteSegments, type RouteLeg } from './geo'
import type { TripScheduleItem } from '@/api/trip'

export type RouteNodeKind = 'hotel' | 'spot' | 'food'

export interface RouteNode {
  kind: RouteNodeKind
  title: string
  /** 计划时间段，如「09:00-11:30」 */
  time?: string
  address?: string
  lat: number
  lng: number
}

/** 住宿信息：有它才能形成真正的闭环（出发与返回都是酒店） */
export interface RouteHotel {
  name: string
  lat: number
  lng: number
  address?: string
}

export interface DayRoute {
  nodes: RouteNode[]
  /** 与 nodes 等长 - 1；规划失败的那一段为 null（调用方按直线兜底） */
  legs: (RouteLeg | null)[]
  /** 全天总里程（公里）；缺值的段不计入 */
  totalKm: number
  /** 全天总驾车耗时（分钟）；缺值的段不计入 */
  totalMin: number
  /** 是否闭环：起点与终点都是住宿 */
  closed: boolean
  /** 定位失败、被剔除的节点名 */
  dropped: string[]
}

function scheduleKind(s: TripScheduleItem): RouteNodeKind {
  return s.type === 'food' ? 'food' : 'spot'
}

/**
 * 构建某一天的闭环路线。
 *
 * @param schedules 当天的行程节点（按时间顺序）
 * @param city      目的地城市，用于提升地理编码命中率
 * @param hotel     住宿；缺省时不闭环，起点为当天第一站、终点为最后一站
 */
export async function buildDayRoute(
  schedules: TripScheduleItem[],
  city: string,
  hotel?: RouteHotel | null
): Promise<DayRoute> {
  const dropped: string[] = []

  // 1) 先按「酒店 → 各站」的顺序铺开，酒店占位在最前
  const seeds: Array<{ name: string; location?: string; lat?: number; lng?: number; node: RouteNode }> = []
  if (hotel) {
    seeds.push({
      name: hotel.name,
      location: hotel.address,
      lat: hotel.lat,
      lng: hotel.lng,
      node: { kind: 'hotel', title: hotel.name, address: hotel.address, lat: hotel.lat, lng: hotel.lng }
    })
  }
  for (const s of schedules) {
    seeds.push({
      name: s.title,
      location: s.location,
      lat: s.lat,
      lng: s.lng,
      node: {
        kind: scheduleKind(s),
        title: s.title,
        time: s.time,
        address: s.location,
        lat: 0,
        lng: 0
      }
    })
  }

  // 2) 批量定位（已带坐标的会直接复用，不重复请求）
  const coords = await geocodeNodes(seeds, city)

  const nodes: RouteNode[] = []
  seeds.forEach((seed, i) => {
    const ll = coords[i]
    if (!ll) {
      // 定位不到的点直接剔除：留在路线里会算出一段通向 (0,0) 的假路线
      dropped.push(seed.name)
      return
    }
    nodes.push({ ...seed.node, lat: ll.lat, lng: ll.lng })
  })

  // 3) 闭环：末尾补回酒店。
  //    必须用同一份坐标，否则「出发的酒店」和「返回的酒店」会落到两个位置。
  let closed = false
  if (hotel && nodes.length > 0 && nodes[0].kind === 'hotel') {
    closed = true
    nodes.push({ ...nodes[0] })
  }

  // 4) 分段规划（并发 3；单段失败不影响其他段）
  const legs = nodes.length >= 2 ? await planRouteSegments(nodes) : []

  let totalKm = 0
  let totalMin = 0
  for (const leg of legs) {
    if (leg?.distanceKm != null) totalKm += leg.distanceKm
    if (leg?.durationMin != null) totalMin += leg.durationMin
  }

  return {
    nodes,
    legs,
    totalKm: Math.round(totalKm * 10) / 10,
    totalMin: Math.round(totalMin),
    closed,
    dropped
  }
}

/** 把分钟数格式化成「1 小时 05 分」这类可读文案 */
export function formatDuration(min: number | null): string {
  if (min == null || !Number.isFinite(min)) return '—'
  if (min < 60) return `${Math.round(min)} 分钟`
  const h = Math.floor(min / 60)
  const m = Math.round(min % 60)
  return m === 0 ? `${h} 小时` : `${h} 小时 ${String(m).padStart(2, '0')} 分`
}

/** 公里数文案：不足 1 公里时用米，避免出现「0.3 公里」这种读起来别扭的值 */
export function formatDistance(km: number | null): string {
  if (km == null || !Number.isFinite(km)) return '—'
  if (km < 1) return `${Math.round(km * 1000)} 米`
  return `${km.toFixed(1)} 公里`
}
