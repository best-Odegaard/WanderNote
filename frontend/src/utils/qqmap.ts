/**
 * 腾讯地图 JS SDK 加载器（幂等）
 * 供 TripMap（H5 渲染）使用：多次调用只注入一次 <script>，返回同一个 Promise。
 * 文档：https://lbs.qq.com/webApi/javascriptGL/glGuide/glBasic
 *
 * 说明：地理编码不走此 SDK（其服务模块 URL 已失效 404），
 * 前端 H5 改用 WebService JSONP（见 geo.ts），App/小程序用 WebService uni.request。
 */
import { MAP_JS_KEY } from './constant'

declare global {
  interface Window {
    TMap?: any
    [key: string]: any
  }
}

let sdkPromise: Promise<any> | null = null

/**
 * 加载腾讯地图 JS API GL（主 SDK），返回 TMap 全局对象
 * - 已加载则直接返回；未配置 VITE_MAP_KEY 或加载失败时 reject
 */
export function loadQqMapSdk(): Promise<any> {
  if (sdkPromise) return sdkPromise

  sdkPromise = new Promise((resolve, reject) => {
    if (typeof window === 'undefined') {
      reject(new Error('no window (非浏览器环境)'))
      return
    }
    const win = window as any
    if (win.TMap) {
      resolve(win.TMap)
      return
    }
    if (!MAP_JS_KEY) {
      console.warn('[qqmap] 未配置 VITE_MAP_KEY，地图 SDK 无法加载')
      reject(new Error('missing VITE_MAP_KEY'))
      return
    }
    // 全局回调名唯一，避免与页面其他脚本冲突
    const cb = `__qqmap_sdk_cb_${Date.now()}_${Math.floor(Math.random() * 1e6)}`
    win[cb] = () => {
      delete win[cb]
      resolve(win.TMap)
    }
    const s = document.createElement('script')
    s.src = `https://map.qq.com/api/gljs?v=1.exp&key=${encodeURIComponent(MAP_JS_KEY)}&callback=${cb}`
    s.async = true
    s.onerror = () => {
      delete win[cb]
      reject(new Error('腾讯地图 SDK 加载失败'))
    }
    document.head.appendChild(s)
  })
  return sdkPromise
}

/** 同步获取已加载的 TMap（未加载时返回 null） */
export function getTMap(): any | null {
  if (typeof window !== 'undefined' && (window as any).TMap) {
    return (window as any).TMap
  }
  return null
}
