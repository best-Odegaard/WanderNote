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
 * 驾车路线规划（默认打车/驾车模式）：
 * 按景点顺序 from -> waypoints -> to，返回沿道路的路径点数组。
 * 失败返回 null，调用方降级为景点直线连接。
 * @param points 有序景点坐标（>=2）
 */
export function planDrivingRoute(points: LatLng[]): Promise<RoutePoint[] | null> {
  if (!MAP_WS_KEY || points.length < 2) return Promise.resolve(null)

  const from = points[0]
  const to = points[points.length - 1]
  const waypoints = points.slice(1, -1)
  const base =
    `https://apis.map.qq.com/ws/direction/v1/driving/` +
    `?from=${from.lat},${from.lng}` +
    `&to=${to.lat},${to.lng}` +
    (waypoints.length ? `&waypoints=${waypoints.map((p) => `${p.lat},${p.lng}`).join(';')}` : '') +
    `&key=${encodeURIComponent(MAP_WS_KEY)}`

  // #ifdef H5
  return jsonpRequest(base).then((data: any) => {
    if (data?.status === 0 && data.result?.routes?.[0]?.polyline) {
      return decodeRoutePolyline(data.result.routes[0].polyline)
    }
    console.warn('[geo] 驾车路线规划失败:', data?.message || data?.status)
    return null
  })
  // #endif
  // #ifndef H5
  return new Promise((resolve) => {
    uni.request({
      url: base,
      method: 'GET',
      timeout: 12000,
      success: (res) => {
        const data = res.data as any
        if (data?.status === 0 && data.result?.routes?.[0]?.polyline) {
          resolve(decodeRoutePolyline(data.result.routes[0].polyline))
        } else {
          console.warn('[geo] 驾车路线规划失败:', data?.message || data?.status)
          resolve(null)
        }
      },
      fail: (err) => {
        console.warn('[geo] 驾车路线规划请求失败:', err)
        resolve(null)
      }
    })
  })
  // #endif
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
