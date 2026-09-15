/**
 * 我的行程卡片：视图模型与派生逻辑
 *
 * 行程卡片需要的展示字段（状态、日期区间、地点数）都要从 TripPlan 现算，
 * 所以集中放在这里，避免页面里堆散落的计算逻辑。
 *
 * 状态说明：后端 TripPlan.status 字段前端从未使用、语义不明，这里不使用它，
 * 而是按 startDate / endDate 与今天的先后关系本地推导（需求确认的口径）。
 */
import type { TripPlan } from '@/api/trip'

/** 卡片状态：待出行 / 进行中 / 已结束 */
export type TripCardStatus = 'pending' | 'ongoing' | 'ended'

export interface MyPlanItem {
  id: number | string
  title: string
  /** 行程天数 */
  days: number
  /** 晚数 = 天数 - 1（至少 0） */
  nights: number
  /** 地点总数（各天日程条目累加） */
  placeCount: number
  /** 出发地 → 目的地，用于无封面时的兜底展示 */
  route: string
  cover?: string
  avatar?: string
  /** 日期区间文案，如「11.26至11.29」；无出发日期时为空 */
  dateRange: string
  status: TripCardStatus
}

/** 从「2026-11-26」「2026-11-26 00:00:00」「2026/11/26」等取本地零点日期，取不到返回 null */
function parseDate(value?: string): Date | null {
  if (!value) return null
  const m = String(value).match(/(\d{4})[-/](\d{1,2})[-/](\d{1,2})/)
  if (!m) return null
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
  return Number.isNaN(d.getTime()) ? null : d
}

/** 今天本地零点（避免时分秒导致边界日判断错位） */
function todayStart(): Date {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}

/** 在给定日期上加天数并返回新日期 */
function addDays(base: Date, days: number): Date {
  const d = new Date(base)
  d.setDate(d.getDate() + days)
  return d
}

/** 行程的有效起止日期：endDate 缺失时按 days 推算，保证日期区间与状态判定一致 */
function resolveRange(trip: TripPlan): { start: Date | null; end: Date | null } {
  const start = parseDate(trip.startDate)
  if (!start) return { start: null, end: null }

  const explicitEnd = parseDate(trip.endDate)
  if (explicitEnd) return { start, end: explicitEnd }

  // 无结束日期时按「N天」推最后一个自然日（3天 → 第1天起算第3天）
  const days = Number(trip.days) > 0 ? Number(trip.days) : 1
  return { start, end: addDays(start, days - 1) }
}

/** 「11.26」格式：月.日，不补前导零 */
function formatMonthDay(d: Date): string {
  return `${d.getMonth() + 1}.${d.getDate()}`
}

/** 日期区间文案：两端都有才拼成「11.26至11.29」，否则为空 */
function formatDateRange(start: Date | null, end: Date | null): string {
  if (!start) return ''
  if (!end || formatMonthDay(start) === formatMonthDay(end)) return formatMonthDay(start)
  return `${formatMonthDay(start)}至${formatMonthDay(end)}`
}

/** 累计地点数：各天 schedules 条目数之和 */
function countPlaces(trip: TripPlan): number {
  const days = Array.isArray(trip.dayPlans) ? trip.dayPlans : []
  return days.reduce((sum, d) => sum + (Array.isArray(d?.schedules) ? d.schedules.length : 0), 0)
}

/**
 * 封面图：优先用 cover 字段；为空时退到行程内首个有图的日程。
 * 后端 dayPlans 里的日程带 AI 生成的景区图（image / photos），实测比 cover 更常可用，
 * 所以这里把「有没有图」的判断收在一处，页面不用关心来源。
 */
function resolveCover(trip: TripPlan): string | undefined {
  if (trip.cover) return trip.cover

  const days = Array.isArray(trip.dayPlans) ? trip.dayPlans : []
  for (const day of days) {
    const schedules = Array.isArray(day?.schedules) ? day.schedules : []
    for (const schedule of schedules) {
      const img = schedule?.image || schedule?.photos?.[0]
      if (img) return img
    }
  }
  return undefined
}

/**
 * 推导卡片状态：
 * - 没有出发日期 → 待出行（还是个没定时间的计划）
 * - 今天早于出发日 → 待出行
 * - 今天晚于结束日 → 已结束
 * - 其余（含区间内）→ 进行中
 */
export function deriveTripCardStatus(trip: TripPlan): TripCardStatus {
  const { start, end } = resolveRange(trip)
  if (!start || !end) return 'pending'

  const today = todayStart()
  if (today.getTime() < start.getTime()) return 'pending'
  if (today.getTime() > end.getTime()) return 'ended'
  return 'ongoing'
}

/** TripPlan → 卡片视图模型；index 用于底色循环 */
export function toMyPlanItem(trip: TripPlan, index = 0): MyPlanItem {
  const days = Number(trip.days) > 0 ? Number(trip.days) : 1
  const { start, end } = resolveRange(trip)
  const route = [trip.fromCity, trip.toCity].filter(Boolean).join(' → ')

  return {
    id: trip.id ?? `trip-${index}`,
    title: trip.title || '未命名行程',
    days,
    nights: Math.max(0, days - 1),
    placeCount: countPlaces(trip),
    route,
    cover: resolveCover(trip),
    dateRange: formatDateRange(start, end),
    status: deriveTripCardStatus(trip)
  }
}

/** 卡片底色令牌：四种浅色按顺序循环 */
const TRIP_BG_TOKENS = ['var(--trip-bg-1)', 'var(--trip-bg-2)', 'var(--trip-bg-3)', 'var(--trip-bg-4)']

export function tripCardBg(index: number): string {
  return TRIP_BG_TOKENS[((index % TRIP_BG_TOKENS.length) + TRIP_BG_TOKENS.length) % TRIP_BG_TOKENS.length]
}
