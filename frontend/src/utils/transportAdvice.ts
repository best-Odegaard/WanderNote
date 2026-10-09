/**
 * 交通方式推荐（高铁/动车 · 飞机 · 自驾）。
 *
 * 定位：**不接任何交通数据源**，只用「两地距离 + 速度/成本系数」给一个有依据的建议，
 * 让用户在行程里先定「怎么去」，再去订票页挑具体车次/航班。
 *   · 为什么不做实时班次：12306 与航司都没有公开开放 API（见 产品项目文档 5.7.1），
 *     拿授权数据源是商务问题，不该卡住「先给用户一个选择」这件事；
 *   · 所以这里的所有耗时与费用都是**估算参考值**，页面上必须写明「估算」，
 *     禁止出现「最快」「保证」这类承诺。
 *
 * 距离来源分两档（结果里的 basis 会告诉页面用的是哪一档）：
 *   1. route    —— 腾讯地图驾车路线规划（真实沿路里程与耗时），与路线页同一条链路；
 *   2. estimate —— 拿不到路线规划时，用直线距离 × 绕路系数 1.3 估算。
 * 两档都拿不到（城市名地理编码失败）→ 返回 null，页面显示「拿不到距离，无法推荐」，
 * 不编一个假方案给用户。
 *
 * 系数是行业经验值，都写成了常量，调优只动这一处（页面文案不用改）：
 *   高铁 0.45 元/km、飞机 0.6 元/km + 机建燃油 80、自驾 0.8 元/km（油费+过路费，整车非人均）。
 */
import { geocode, planSingleLeg, type LatLng } from '@/utils/geo'
import { formatDuration, type TransportMode } from '@/utils/tickets'

/** 高铁均速（km/h，含停站） */
const HIGH_SPEED_KMH = 250
/** 飞机巡航均速（km/h） */
const FLIGHT_SPEED_KMH = 750
/** 驾车均速（km/h，含进城与休息） */
const DRIVE_SPEED_KMH = 70

/** 高铁门到门附加时间（分钟）：去站 + 候车 + 两端接驳 */
const TRAIN_ACCESS_MIN = 45
/** 飞机门到门附加时间（分钟）：提前 2 小时到机场 + 往返市区 + 起降滑行 */
const FLIGHT_ACCESS_MIN = 150

/**
 * 直线距离 × 绕路系数 ≈ 实际路程。
 * 1.3 是路网绕行的常见量级；只在地图路线规划不可用时才用，页面会标注「按直线估算」。
 */
const DETOUR_RATIO = 1.3
/** 拿不到驾车路线时，自驾耗时按这个均速反推 */
const FALLBACK_SPEED_KMH = 70

const TRAIN_COST_PER_KM = 0.45
const FLIGHT_COST_PER_KM = 0.6
/** 机建 + 燃油附加（元），机票估算必须带上，否则低价航线会显得比高铁还便宜 */
const FLIGHT_FIXED_FEE = 80
const DRIVE_COST_PER_KM = 0.8

/** 推荐分档阈值（公里） */
const FLIGHT_BEST_KM = 900
const HIGH_SPEED_BEST_KM = 100

export interface TransportAdvice {
  mode: TransportMode
  /** 门到门耗时（分钟，含两端接驳） */
  durationMin: number
  /** 里程（公里） */
  distanceKm: number
  /** 估算费用下限（元/人；自驾为整车油费+过路费） */
  costFrom: number
  /** 估算费用上限（元/人）；与下限相同表示单一估算值 */
  costTo: number
  /** 是否本题线路的推荐项（每题只有一项为 true） */
  recommended: boolean
  /** 一句话依据：为什么推荐它 / 为什么不推荐它 */
  reason: string
}

export interface TransportAdviceResult {
  list: TransportAdvice[]
  /** 距离来源：route=地图路线规划，estimate=直线距离估算 */
  basis: 'route' | 'estimate'
  distanceKm: number
}

/** 同一条线路只算一次（切去程/返程会重复问同一个距离） */
const cache = new Map<string, TransportAdviceResult | null>()

function round(n: number, step = 1): number {
  return Math.round(n / step) * step
}

/** 直线距离（公里） */
function haversineKm(a: LatLng, b: LatLng): number {
  const R = 6371
  const toRad = (d: number) => (d * Math.PI) / 180
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const lat1 = toRad(a.lat)
  const lat2 = toRad(b.lat)
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)))
}

/**
 * 按两座城市推荐交通方式。
 *
 * @returns 三种方式（高铁/飞机/自驾）+ 推荐标记；拿不到距离时返回 null
 */
export async function recommendTransports(
  fromCity?: string,
  toCity?: string
): Promise<TransportAdviceResult | null> {
  const from = (fromCity || '').trim()
  const to = (toCity || '').trim()
  if (!from || !to || from === to) return null

  const key = `${from}→${to}`
  if (cache.has(key)) return cache.get(key) ?? null

  let result: TransportAdviceResult | null = null
  try {
    // 地理编码按城市名查：两个都拿不到坐标就没法给距离，如实返回 null
    const [a, b] = await Promise.all([geocode(from, from, from), geocode(to, to, to)])
    if (a && b) {
      const leg = await planSingleLeg(a, b)
      let distanceKm: number
      let basis: 'route' | 'estimate'
      let driveMin: number
      if (leg?.distanceKm) {
        distanceKm = leg.distanceKm
        basis = 'route'
        driveMin = leg.durationMin ?? Math.round((distanceKm / DRIVE_SPEED_KMH) * 60)
      } else {
        distanceKm = haversineKm(a, b) * DETOUR_RATIO
        basis = 'estimate'
        driveMin = Math.round((distanceKm / FALLBACK_SPEED_KMH) * 60)
      }
      result = buildAdvice(distanceKm, basis, driveMin)
    } else {
      console.warn('[transport] 地理编码失败，无法按距离推荐：', from, to)
    }
  } catch (e) {
    console.warn('[transport] 推荐计算失败:', e)
  }

  cache.set(key, result)
  return result
}

/** 纯计算（不依赖网络），便于单测与调参 */
function buildAdvice(
  distanceKm: number,
  basis: 'route' | 'estimate',
  driveMin: number
): TransportAdviceResult {
  const d = Math.max(1, distanceKm)
  const trainMin = Math.round(TRAIN_ACCESS_MIN + (d / HIGH_SPEED_KMH) * 60)
  const flightMin = Math.round(FLIGHT_ACCESS_MIN + (d / FLIGHT_SPEED_KMH) * 60)

  const trainCost = Math.max(20, round(d * TRAIN_COST_PER_KM))
  const flightCost = Math.max(300, round(d * FLIGHT_COST_PER_KM + FLIGHT_FIXED_FEE, 10))
  // 自驾是「整车」口径：一口价，所以上下限相同
  const driveCost = Math.max(20, round(d * DRIVE_COST_PER_KM, 10))

  // ── 推荐规则：先说人话可解释，再谈精确 ──
  //   900km 以上：高铁门到门时间已经明显吃亏 → 飞机
  //   100~900km：高铁优势区间（不用提前 2 小时到机场）→ 高铁
  //   100km 以内：自驾门到门最灵活也最便宜 → 自驾
  let best: TransportMode
  if (d >= FLIGHT_BEST_KM) best = 'flight'
  else if (d >= HIGH_SPEED_BEST_KM) best = 'train'
  else best = 'drive'

  const hours = (min: number) => (min / 60).toFixed(1)

  const list: TransportAdvice[] = [
    {
      mode: 'train',
      durationMin: trainMin,
      distanceKm: Math.round(d),
      costFrom: trainCost,
      costTo: trainCost,
      recommended: best === 'train',
      reason:
        best === 'train'
          ? `${Math.round(d)}km 是高铁的优势区间：不用提前 2 小时到机场，门到门通常最省心`
          : d >= 1500
            ? `超长途，门到门约 ${hours(trainMin)} 小时，建议改坐飞机`
            : `门到门约 ${formatDuration(trainMin)}，比飞机省去安检与往返机场的时间`
    },
    {
      mode: 'flight',
      durationMin: flightMin,
      distanceKm: Math.round(d),
      costFrom: flightCost,
      costTo: round(flightCost * 1.4, 10),
      recommended: best === 'flight',
      reason:
        best === 'flight'
          ? `单程 ${Math.round(d)}km，坐高铁门到门要 ${hours(trainMin)} 小时，飞机省下的时间值得`
          : `短途飞行省不下时间：安检 + 往返机场约 2.5 小时`
    },
    {
      mode: 'drive',
      durationMin: driveMin,
      distanceKm: Math.round(d),
      costFrom: driveCost,
      costTo: driveCost,
      recommended: best === 'drive',
      reason:
        best === 'drive'
          ? `不到 ${HIGH_SPEED_BEST_KM}km，自驾门到门最灵活，费用也是三者里最低的`
          : d > 800
            ? `单程 ${Math.round(d)}km、要开约 ${hours(driveMin)} 小时，偏累，建议中途住一晚或改高铁/飞机`
            : `自驾约 ${formatDuration(driveMin)}，适合想沿途停留的玩法`
    }
  ]

  return { list, basis, distanceKm: Math.round(d) }
}
