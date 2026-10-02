/**
 * 腾讯地图工具 - 地理编码 + 驾车路线规划
 * 把行程景点的名称/地址解析为经纬度，并按景点顺序规划真实驾车路径，
 * 供 TripMap 绘制标记与沿道路的路线。
 *
 * 平台策略：
 * - H5：WebService 的 JSONP 调用（output=jsonp），绕过浏览器 CORS 限制；
 *   需在腾讯控制台为该 Key 配置授权 IP（或设为不限制）
 * - App/小程序：WebService 的 uni.request 调用（无浏览器 CORS 限制）
 *
 * 说明：
 * - 内存缓存，避免同一景点重复请求
 * - 失败时降级返回 null（坐标缺失/路径缺失时调用方按直线兜底），不阻断整体绘制
 */
import { MAP_WS_KEY } from './constant'

export interface LatLng {
  lat: number
  lng: number
}

/** 路线规划返回的路径点（沿道路） */
export interface RoutePoint extends LatLng {}

export interface GeoSpot {
  /** 景点名称（用于展示与作为地址补充） */
  name: string
  /** 详细地址（优先用于地理编码；缺省时用 name） */
  address?: string
  /** 所属城市，提升地理编码精度 */
  city?: string
  /** 已有经纬度则直接使用，跳过 geocode */
  lat?: number
  lng?: number
}

/** 内存缓存：key = `${city}|${address}` -> LatLng | null */
const geoCache = new Map<string, LatLng | null>()

function cacheKey(address: string, city?: string): string {
  return `${city || ''}|${address}`
}

// ===== 通用 JSONP（H5，绕过 CORS） =====
// #ifdef H5
function jsonpRequest(url: string, timeoutMs = 12000): Promise<any> {
  return new Promise((resolve) => {
    const cb = `__qq_jsonp_${Date.now()}_${Math.floor(Math.random() * 1e6)}`
    let timer: ReturnType<typeof setTimeout> | null = null
    const win = window as any
    win[cb] = (data: any) => {
      if (timer) clearTimeout(timer)
      cleanup()
      resolve(data)
    }
    function cleanup() {
      delete win[cb]
      if (s.parentNode) s.parentNode.removeChild(s)
    }
    const s = document.createElement('script')
    s.src = `${url}&output=jsonp&callback=${cb}`
    s.async = true
    s.onerror = () => {
      if (timer) clearTimeout(timer)
      cleanup()
      console.warn('[geo] JSONP 请求失败:', url.slice(0, 120))
      resolve(null)
    }
    document.head.appendChild(s)
    timer = setTimeout(() => {
      cleanup()
      console.warn('[geo] JSONP 请求超时:', url.slice(0, 120))
      resolve(null)
    }, timeoutMs)
  })
}
// #endif

// ===== 地理编码 =====

/** 方式一（App/小程序）：WebService geocoder 的 uni.request 调用 */
function geocodeByRequest(query: string, city?: string): Promise<LatLng | null> {
  if (!MAP_WS_KEY) return Promise.resolve(null)

  const url =
    `https://apis.map.qq.com/ws/geocoder/v1/?address=${encodeURIComponent(query)}` +
    `&key=${encodeURIComponent(MAP_WS_KEY)}` +
    (city ? `&city=${encodeURIComponent(city)}` : '')

  return new Promise<LatLng | null>((resolve) => {
    uni.request({
      url,
      method: 'GET',
      timeout: 8000,
      success: (res) => {
        const data = res.data as any
        if (data && data.status === 0 && data.result?.location) {
          resolve({ lat: data.result.location.lat, lng: data.result.location.lng })
        } else {
          console.warn('[geo] 地理编码失败:', query, data?.message || data?.status)
          resolve(null)
        }
      },
      fail: (err) => {
        console.warn('[geo] 地理编码请求失败:', query, err)
        resolve(null)
      }
    })
  })
}

/** 方式二（H5）：WebService geocoder 的 JSONP 调用 */
// #ifdef H5
function geocodeByJsonp(query: string, city?: string): Promise<LatLng | null> {
  if (!MAP_WS_KEY) return Promise.resolve(null)

  const url =
    `https://apis.map.qq.com/ws/geocoder/v1/?address=${encodeURIComponent(query)}` +
    `&key=${encodeURIComponent(MAP_WS_KEY)}` +
    (city ? `&city=${encodeURIComponent(city)}` : '')

  return jsonpRequest(url).then((data: any) => {
    if (data && data.status === 0 && data.result?.location) {
      return { lat: data.result.location.lat, lng: data.result.location.lng }
    }
    console.warn('[geo] 地理编码失败:', query, data?.message || data?.status)
    return null
  })
}
// #endif

/**
 * 地址/名称 -> 经纬度
 * 注意：腾讯 geocoder 对过简地址（如"七星岩"）会返回参数错误(348)，
 * 需拼接城市（"肇庆市七星岩"）后再请求。
 */
export function geocode(address: string, city?: string, name?: string): Promise<LatLng | null> {
  let query = address || name || ''
  if (!query) return Promise.resolve(null)
  // 地址不含城市时拼接城市前缀，提升识别率
  if (city && !query.includes(city)) {
    query = `${city}${query}`
  }

  const key = cacheKey(query, city)
  if (geoCache.has(key)) return Promise.resolve(geoCache.get(key) ?? null)

  // #ifdef H5
  return geocodeByJsonp(query, city).then((ll) => {
    geoCache.set(key, ll)
    return ll
  })
  // #endif
  // #ifndef H5
  return geocodeByRequest(query, city).then((ll) => {
    geoCache.set(key, ll)
    return ll
  })
  // #endif
}

// ===== 逆地理编码（坐标 -> 城市） =====
//
// 用途：出发地槽位的「📍 用当前位置」—— 用户点一下就把所在城市填进出发地。
// 与 geocode 互为反向：geocode 是「地址 -> 坐标」，这里是「坐标 -> 城市」。
//
// 注意：出发地会直接影响车票查询与首日路线，所以这里的策略是**宁可失败也不猜** ——
// 任何一步拿不到就返回 null，由调用方提示用户手动选，而不是塞一个默认城市进去。

/** 从腾讯逆地理编码响应里取城市名（去掉「市」后缀，与槽位选项里的城市名对齐） */
function pickCityFromReverse(data: any): string | null {
  const comp = data?.result?.address_component
  // 直辖市没有 city 字段，回落到 province；两者都拿不到就放弃
  const raw = comp?.city || comp?.province || ''
  const city = String(raw).trim().replace(/市$/, '')
  return city || null
}

/** 方式一（App/小程序）：逆地理编码的 uni.request 调用 */
function reverseGeocodeByRequest(lat: number, lng: number): Promise<string | null> {
  if (!MAP_WS_KEY) return Promise.resolve(null)

  const url =
    `https://apis.map.qq.com/ws/geocoder/v1/?location=${lat},${lng}` +
    `&key=${encodeURIComponent(MAP_WS_KEY)}`

  return new Promise<string | null>((resolve) => {
    uni.request({
      url,
      method: 'GET',
      timeout: 8000,
      success: (res) => {
        const data = res.data as any
        const city = pickCityFromReverse(data)
        if (!city) {
          console.warn('[geo] 逆地理编码失败:', data?.message || data?.status)
        }
        resolve(city)
      },
      fail: (err) => {
        console.warn('[geo] 逆地理编码请求失败:', err)
        resolve(null)
      }
    })
  })
}

/** 方式二（H5）：逆地理编码的 JSONP 调用（同 geocode，绕 CORS） */
// #ifdef H5
function reverseGeocodeByJsonp(lat: number, lng: number): Promise<string | null> {
  if (!MAP_WS_KEY) return Promise.resolve(null)

  const url =
    `https://apis.map.qq.com/ws/geocoder/v1/?location=${lat},${lng}` +
    `&key=${encodeURIComponent(MAP_WS_KEY)}`

  return jsonpRequest(url).then((data: any) => pickCityFromReverse(data))
}
// #endif

/** 坐标 -> 城市名；失败返回 null */
export function reverseGeocodeCity(lat: number, lng: number): Promise<string | null> {
  // #ifdef H5
  return reverseGeocodeByJsonp(lat, lng)
  // #endif
  // #ifndef H5
  return reverseGeocodeByRequest(lat, lng)
  // #endif
}

/**
 * 取当前经纬度。
 *
 * 优先用 uni.getLocation：App/小程序端走系统原生定位，最可靠。
 * 失败再回落浏览器定位。两者坐标系不同（uni 要 gcj02，navigator 返回 wgs84），
 * 但城市级只差几百米、不影响「反查出是哪个城市」，所以这里不做坐标转换。
 */
function getCurrentLatLng(): Promise<LatLng | null> {
  return new Promise((resolve) => {
    try {
      uni.getLocation({
        type: 'gcj02',
        success: (res: any) => {
          const lat = Number(res?.latitude)
          const lng = Number(res?.longitude)
          resolve(Number.isFinite(lat) && Number.isFinite(lng) ? { lat, lng } : null)
        },
        fail: (err: any) => {
          console.warn('[geo] uni.getLocation 失败，尝试浏览器定位:', err?.errMsg || err)
          resolve(getLatLngByNavigator())
        }
      })
    } catch (e) {
      console.warn('[geo] uni.getLocation 不可用，尝试浏览器定位:', e)
      resolve(getLatLngByNavigator())
    }
  })
}

/**
 * H5 兜底：浏览器原生定位。
 * ⚠️ 非 https 环境浏览器会直接拒绝调用（线上是 http://…，就是这种情况），
 * 此时只能返回 null 让用户手动选出发城市。
 */
function getLatLngByNavigator(): Promise<LatLng | null> {
  // #ifdef H5
  if (typeof navigator !== 'undefined' && navigator.geolocation) {
    return new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude }),
        (err) => {
          console.warn('[geo] 浏览器定位失败:', err?.message)
          resolve(null)
        },
        { timeout: 8000, enableHighAccuracy: false }
      )
    })
  }
  // #endif
  return Promise.resolve(null)
}

/** 定位 + 逆地理编码，拿到当前所在城市；任一步失败都返回 null */
export async function locateCurrentCity(): Promise<string | null> {
  const ll = await getCurrentLatLng()
  if (!ll) return null
  return reverseGeocodeCity(ll.lat, ll.lng)
}

// ===== 驾车路线规划（真实路径，默认驾车/打车） =====

/**
 * 解码腾讯驾车路线 polyline
 * - 新版：坐标增量数组 [起lat, 起lng, Δlat, Δlng, ...]（Δ 单位 1e-6 度）
 * - 旧版：加密字符串（腾讯 polyline 编码，坐标缩放 1e6）
 */
export function decodeRoutePolyline(polyline: unknown): RoutePoint[] {
  if (Array.isArray(polyline)) {
    const pts: RoutePoint[] = []
    if (polyline.length < 2) return pts
    let lat = polyline[0] as number
    let lng = polyline[1] as number
    pts.push({ lat, lng })
    for (let i = 2; i + 1 < polyline.length; i += 2) {
      lat += (polyline[i] as number) / 1e6
      lng += (polyline[i + 1] as number) / 1e6
      pts.push({ lat, lng })
    }
    return pts
  }

  // 旧版加密字符串
  const str = String(polyline)
  const pts: RoutePoint[] = []
  let index = 0
  let lat = 0
  let lng = 0
  while (index < str.length) {
    let b: number
    let shift = 0
    let result = 0
    do {
      b = str.charCodeAt(index++) - 63
      result |= (b & 0x1f) << shift
      shift += 5
    } while (b >= 0x20)
    const dLat = result & 1 ? ~(result >> 1) : result >> 1
    lat += dLat
    shift = 0
    result = 0
    do {
      b = str.charCodeAt(index++) - 63
      result |= (b & 0x1f) << shift
      shift += 5
    } while (b >= 0x20)
    const dLng = result & 1 ? ~(result >> 1) : result >> 1
    lng += dLng
    pts.push({ lat: lat / 1e6, lng: lng / 1e6 })
  }
  return pts
}

/**
 * 腾讯驾车路线返回的单条 route（只声明用到的字段）
 *
 * 量纲实测（2026-09，广州塔 → 珠江新城，5.5km）：
 *   distance = 5524（米）、duration = 15（分钟）
 * 注意 duration 是分钟不是秒，别再做除法。
 */
interface TencentRoute {
  polyline?: unknown
  /** 耗时（分钟） */
  duration?: number
  /** 距离（米） */
  distance?: number
}

/** 统一解析腾讯驾车响应，取第一条路线；失败打印原因并返回 null */
function pickRoute(data: any): TencentRoute | null {
  if (data?.status === 0 && data?.result?.routes?.[0]) {
    return data.result.routes[0] as TencentRoute
  }
  console.warn('[geo] 驾车路线规划失败:', data?.message || data?.status)
  return null
}

/**
 * 请求腾讯驾车路线。
 *
 * waypoints 上限：官方文档没有明确写死，实测传 12 个途经点仍返回 status=0。
 * 但本函数只用于「一段路」（from→to）和「整天一次规划」两种场景，
 * 拿分段耗时请用 planRouteSegments（它会拆成多次两点的请求）。
 */
function fetchDrivingRoute(
  from: LatLng,
  to: LatLng,
  waypoints: LatLng[] = []
): Promise<TencentRoute | null> {
  if (!MAP_WS_KEY) return Promise.resolve(null)

  const base =
    `https://apis.map.qq.com/ws/direction/v1/driving/` +
    `?from=${from.lat},${from.lng}` +
    `&to=${to.lat},${to.lng}` +
    (waypoints.length ? `&waypoints=${waypoints.map((p) => `${p.lat},${p.lng}`).join(';')}` : '') +
    `&key=${encodeURIComponent(MAP_WS_KEY)}`

  // #ifdef H5
  return jsonpRequest(base).then(pickRoute)
  // #endif
  // #ifndef H5
  return new Promise((resolve) => {
    uni.request({
      url: base,
      method: 'GET',
      timeout: 12000,
      success: (res) => resolve(pickRoute(res.data)),
      fail: (err) => {
        console.warn('[geo] 驾车路线规划请求失败:', err)
        resolve(null)
      }
    })
  })
  // #endif
}

/**
 * 驾车路线规划（默认打车/驾车模式）：
 * 按景点顺序 from -> waypoints -> to，返回沿道路的路径点数组。
 * 失败返回 null，调用方降级为景点直线连接。
 * @param points 有序景点坐标（>=2）
 */
export async function planDrivingRoute(points: LatLng[]): Promise<RoutePoint[] | null> {
  if (!MAP_WS_KEY || points.length < 2) return null
  const route = await fetchDrivingRoute(points[0], points[points.length - 1], points.slice(1, -1))
  return route ? decodeRoutePolyline(route.polyline) : null
}

/** 一段路的规划结果 */
export interface RouteLeg {
  /** 沿道路的路径点 */
  path: RoutePoint[]
  /** 驾车耗时（分钟）；拿不到为 null */
  durationMin: number | null
  /** 距离（公里）；拿不到为 null */
  distanceKm: number | null
}

/**
 * 规划「一段」路：from → to，同时拿到耗时与距离。
 *
 * 为什么不复用 planDrivingRoute：它只返回 polyline。
 * 而「第 2/5 段 · 下一站：七星岩」这种接续导航体验，必须要每段各自的耗时/距离，
 * 一次带动全部途经点的请求只会给出整条路线的合计值，拿不到分段数据。
 */
export async function planSingleLeg(from: LatLng, to: LatLng): Promise<RouteLeg | null> {
  const route = await fetchDrivingRoute(from, to)
  if (!route) return null
  return {
    path: decodeRoutePolyline(route.polyline),
    durationMin: typeof route.duration === 'number' ? route.duration : null,
    distanceKm: typeof route.distance === 'number' ? route.distance / 1000 : null
  }
}

/**
 * 批量规划分段路线（相邻两点为一段）。
 *
 * 并发上限默认 3：整天路线可能有 5-8 段，串行太慢，全并发又容易触发腾讯的 QPS 限制。
 * 单段失败不影响其他段，失败位置返回 null，调用方按直线兜底。
 */
export async function planRouteSegments(
  points: LatLng[],
  concurrency = 3
): Promise<(RouteLeg | null)[]> {
  const count = Math.max(0, points.length - 1)
  const legs: (RouteLeg | null)[] = new Array(count).fill(null)
  if (count === 0 || !MAP_WS_KEY) return legs

  let cursor = 0
  const worker = async () => {
    while (cursor < count) {
      const i = cursor++
      legs[i] = await planSingleLeg(points[i], points[i + 1])
    }
  }
  await Promise.all(
    Array.from({ length: Math.min(Math.max(1, concurrency), count) }, () => worker())
  )
  return legs
}

/**
 * 批量定位一串节点：已带坐标的直接用；否则按「地址 → 名称」逐个地理编码。
 *
 * 关键规则（与行程详情页一致）：当同一 location 被多个节点共用时，判定它没有区分度
 * （常见于 location 被写成「婺源·交通便利」这类标签文本），改用节点名称去解析，
 * 否则所有点会落到同一个坐标、路线退化成一条短线。
 *
 * @returns 与入参等长的数组，解析失败的槽位为 null
 */
export async function geocodeNodes(
  items: Array<{ name: string; location?: string; lat?: number; lng?: number }>,
  city: string
): Promise<(LatLng | null)[]> {
  const locCount = new Map<string, number>()
  for (const it of items) {
    const loc = (it.location || '').trim()
    if (loc) locCount.set(loc, (locCount.get(loc) || 0) + 1)
  }
  const ambiguous = (loc: string) => {
    const t = (loc || '').trim()
    return !!t && (locCount.get(t) || 0) >= 2
  }

  const out: (LatLng | null)[] = []
  for (const it of items) {
    if (it.lat != null && it.lng != null) {
      out.push({ lat: it.lat, lng: it.lng })
      continue
    }
    const name = it.name || ''
    const loc = (it.location || '').trim()
    const preferTitle = ambiguous(loc)
    let ll = preferTitle ? await geocode(name, city, name) : await geocode(loc || name, city, name)
    if (!ll && preferTitle && loc) ll = await geocode(loc, city, name)
    if (!ll && !preferTitle && name) ll = await geocode(name, city, name)
    out.push(ll)
  }
  return out
}

/**
 * 对一批景点逐个地理编码，每解析成功一个立即回调，用于"逐点绘制"动画。
 * - 已自带 lat/lng 的景点直接回传，跳过网络请求
 * - 单个失败不影响后续；只回传解析成功的点
 * @param spots 待解析景点列表
 * @param onEach 每解析成功一个点回调（含原始下标，便于顺序展示）
 * @param intervalMs 相邻点之间的最小间隔（动画节奏），默认 350ms
 */
export async function geocodeSpotsSequential(
  spots: GeoSpot[],
  onEach: (spot: GeoSpot & LatLng, index: number) => void,
  intervalMs = 350
): Promise<void> {
  for (let i = 0; i < spots.length; i++) {
    const s = spots[i]
    let ll: LatLng | null = null
    if (s.lat != null && s.lng != null) {
      ll = { lat: s.lat, lng: s.lng }
    } else {
      ll = await geocode(s.address || s.name, s.city, s.name)
    }
    if (ll) onEach({ ...s, ...ll }, i)
    if (intervalMs > 0 && i < spots.length - 1) {
      await new Promise((r) => setTimeout(r, intervalMs))
    }
  }
}
