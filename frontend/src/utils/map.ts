/**
 * 地图导航工具 — 轻量方案
 * 不内嵌地图、不做地理编码，直接拼第三方地图导航链接，跳转外部地图 App/网页。
 * 导航交互：点击后弹出三选项（复制地址 / 高德导航 / 腾讯导航）。
 * - H5：浏览器无法检测手机是否安装地图 App，直接打开"优先唤起 App"的网页链接，
 *   装了对应 App 会自动拉起进入导航，未安装则展示网页版导航（效果等同）。
 * - App：用 plus.runtime.isApplicationExist 检测高德/腾讯地图是否安装，
 *   已安装用 App scheme 直接唤起，未安装提示并打开网页版兜底。
 */
import { showActionSheet, showToast } from '@/utils/feedback'
import { storage } from '@/utils/storage'

export type NaviMode = 'drive' | 'walk' | 'bus' | 'bike'

/** 导航目标（地点名称 + 详细地址，经纬度可选，有则导航更精准） */
export interface NaviTarget {
  /** 地点名称（景点名），如"星湖景区" */
  title: string
  /** 详细地址，缺省用 title 兜底 */
  address?: string
  /** 经纬度（地理编码后），可选 */
  lat?: number
  lng?: number
  /** 出行方式，默认驾车 */
  mode?: NaviMode
}

/**
 * 腾讯地图路线规划 URI（https://lbs.qq.com/webApi/uriAPI/uriWeb/webUriRoute）
 * 无需 key、无需经纬度：按目的地名称 + 详细地址搜索定位，从当前位置导航。
 * 手机浏览器打开时会唤起腾讯地图 App（已安装时）。
 */
export function buildNaviUrl(title: string, address: string, mode: NaviMode = 'drive'): string {
  const qs = [
    `type=${mode}`,
    `to=${encodeURIComponent(title)}`,
    `toaddr=${encodeURIComponent(address || title)}`,
    `referer=${encodeURIComponent('WanderNote')}`
  ].join('&')
  return `https://apis.map.qq.com/uri/v1/routeplan?${qs}`
}

/** 高德网页版 mode 映射（uri.amap.com） */
const AMAP_WEB_MODE: Record<NaviMode, string> = { drive: 'car', walk: 'walk', bus: 'bus', bike: 'ride' }
/** 高德 App scheme mode 映射（amapuri://，0驾车 1公交 2步行 3骑行） */
const AMAP_APP_MODE: Record<NaviMode, string> = { drive: '0', walk: '2', bus: '1', bike: '3' }

/**
 * 高德地图路线规划 URI（https://lbs.amap.com/api/uri-api/guide/travel/route）
 * callnative=1：手机浏览器打开时优先唤起高德 App，未安装则展示网页版。
 */
export function buildAmapNaviUrl(title: string, address: string, mode: NaviMode = 'drive'): string {
  const qs = [
    `to=${encodeURIComponent(title)}`,
    `toaddr=${encodeURIComponent(address || title)}`,
    `mode=${AMAP_WEB_MODE[mode]}`,
    'policy=1',
    `src=${encodeURIComponent('WanderNote')}`,
    'coordinate=gaode',
    'callnative=1'
  ].join('&')
  return `https://uri.amap.com/navigation?${qs}`
}

/** 高德 App scheme：从当前位置导航到目的地（有经纬度时按坐标定位，更精准） */
export function buildAmapScheme(target: NaviTarget): string {
  const coord =
    target.lat != null && target.lng != null ? `&dlat=${target.lat}&dlon=${target.lng}` : ''
  return `amapuri://route/plan?sourceApplication=${encodeURIComponent('WanderNote')}&sid=BGVIS1&sname=${encodeURIComponent('我的位置')}${coord}&dname=${encodeURIComponent(target.title)}&dev=0&t=${AMAP_APP_MODE[target.mode || 'drive']}`
}

/** 腾讯地图 App scheme：从当前位置导航到目的地 */
export function buildQqMapScheme(target: NaviTarget): string {
  const tocoord =
    target.lat != null && target.lng != null ? `&tocoord=${target.lat},${target.lng}` : ''
  return `qqmap://map/routeplan?type=${target.mode || 'drive'}&from=${encodeURIComponent('我的位置')}&fromcoord=CurrentLocation&to=${encodeURIComponent(target.title)}${tocoord}&referer=${encodeURIComponent('WanderNote')}`
}

// ===== App 端地图 App 安装检测（H5 无法检测，用唤起式链接） =====
// Android 包名 / iOS URL scheme
const AMAP_APP = { pname: 'com.autonavi.minimap', action: 'iosamap://' }
const QQMAP_APP = { pname: 'com.tencent.map', action: 'qqmap://' }
const BAIDU_APP = { pname: 'com.baidu.BaiduMap', action: 'baidumap://' }

/** App 端检测指定地图 App 是否安装；非 App 端恒为 false */
function appInstalled(app: { pname: string; action: string }): boolean {
  // #ifdef APP-PLUS
  const os = plus.os.name.toLowerCase()
  const opt = os === 'ios' ? { action: app.action } : { pname: app.pname }
  return plus.runtime.isApplicationExist(opt)
  // #endif
  // #ifndef APP-PLUS
  return false
  // #endif
}

/** 非 H5/App 端（如小程序）降级：复制导航链接提示 */
function copyLinkFallback(url: string): void {
  uni.setClipboardData({
    data: url,
    success: () => showToast({ title: '导航链接已复制，请到浏览器打开', icon: 'none' })
  })
}

/** 打开高德导航：App 端先检测安装再唤起，H5 打开唤起式链接 */
export function openAmapNavi(target: NaviTarget): void {
  const url = buildAmapNaviUrl(target.title, target.address || '', target.mode)
  // #ifdef APP-PLUS
  if (appInstalled(AMAP_APP)) {
    plus.runtime.openURL(buildAmapScheme(target))
  } else {
    showToast({ title: '未检测到高德地图，已打开网页版', icon: 'none' })
    plus.runtime.openURL(url)
  }
  // #endif
  // #ifdef H5
  window.open(url, '_blank')
  // #endif
  // #ifndef H5 || APP-PLUS
  copyLinkFallback(url)
  // #endif
}

/** 打开腾讯导航：App 端先检测安装再唤起，H5 打开唤起式链接 */
export function openQqMapNavi(target: NaviTarget): void {
  const url = buildNaviUrl(target.title, target.address || '', target.mode)
  // #ifdef APP-PLUS
  if (appInstalled(QQMAP_APP)) {
    plus.runtime.openURL(buildQqMapScheme(target))
  } else {
    showToast({ title: '未检测到腾讯地图，已打开网页版', icon: 'none' })
    plus.runtime.openURL(url)
  }
  // #endif
  // #ifdef H5
  window.open(url, '_blank')
  // #endif
  // #ifndef H5 || APP-PLUS
  copyLinkFallback(url)
  // #endif
}

/** 复制地址信息（景点名 + 详细地址，无地址时仅景点名） */
export function copyAddress(target: NaviTarget): void {
  const text = target.address ? `${target.title}\n${target.address}` : target.title
  uni.setClipboardData({
    data: text,
    success: () => showToast({ title: '地址已复制', icon: 'success' })
  })
}

/**
 * 百度：从当前位置导航到目的地（Web 兜底）。
 *
 * 有坐标走 direction —— coord_type=gcj02 必须有：我们的坐标来自腾讯位置服务（GCJ-02），
 * 百度默认按 BD-09 解析，不声明会偏几百米。
 *
 * 没坐标时退化为百度地图搜索页：硬拼一个无 region 的纯文本 destination 经常搜不到，
 * 不如把用户送到搜索页自己点一下。
 */
export function buildBaiduNaviUrl(target: NaviTarget, mode: NaviMode = 'drive'): string {
  const modeStr =
    mode === 'walk' ? 'walking' : mode === 'bus' ? 'transit' : mode === 'bike' ? 'riding' : 'driving'
  if (target.lat != null && target.lng != null) {
    const dest = baiduPoint({ name: target.title, lat: target.lat, lng: target.lng })
    return (
      `https://api.map.baidu.com/direction?destination=${dest}` +
      `&mode=${modeStr}&coord_type=gcj02&output=html&src=${encodeURIComponent('webapp.wandernote')}`
    )
  }
  return `https://map.baidu.com/search/${encodeURIComponent(target.address || target.title)}`
}

/** 打开百度导航：App 端先检测安装再唤起，H5 打开唤起式链接 */
export function openBaiduNavi(target: NaviTarget): void {
  const mode = target.mode || 'drive'
  const url = buildBaiduNaviUrl(target, mode)
  // #ifdef APP-PLUS
  if (appInstalled(BAIDU_APP)) {
    const dest =
      target.lat != null && target.lng != null
        ? baiduPoint({ name: target.title, lat: target.lat, lng: target.lng })
        : encodeURIComponent(target.title)
    plus.runtime.openURL(
      `baidumap://map/direction?destination=${dest}&coord_type=gcj02&mode=${mode === 'walk' ? 'walking' : 'driving'}&src=android.wandernote`
    )
  } else {
    showToast({ title: '未检测到百度地图，已打开网页版', icon: 'none' })
    plus.runtime.openURL(url)
  }
  // #endif
  // #ifdef H5
  window.open(url, '_blank')
  // #endif
  // #ifndef H5 || APP-PLUS
  copyLinkFallback(url)
  // #endif
}

/**
 * 点击"导航"入口：弹出选项（复制地址 / 高德 / 百度 / 腾讯）
 *
 * 这里的地图清单必须和 openRouteNavi 那套保持一致 ——
 * 同一个 App 里出现两套地图选项，用户会以为少装了东西。
 */
export function showNaviOptions(target: NaviTarget): void {
  showActionSheet({
    itemList: ['复制地址信息', '高德地图导航', '百度地图导航', '腾讯地图导航'],
    success: (res) => {
      if (res.tapIndex === 0) copyAddress(target)
      else if (res.tapIndex === 1) openAmapNavi(target)
      else if (res.tapIndex === 2) openBaiduNavi(target)
      else if (res.tapIndex === 3) openQqMapNavi(target)
    }
  })
}

// ==================== 起终点导航（全天路线 / 接续导航用） ====================

/**
 * 一个导航端点。经纬度必填 —— 全天路线是「酒店 → 景点 → 酒店」的多段规划，
 * 只靠地址文本搜索无法保证各段起终点一致，会出现路线对不上的情况。
 */
export interface NaviPoint {
  name: string
  lat: number
  lng: number
  address?: string
}

export type NaviProvider = 'amap' | 'baidu' | 'qqmap'

export const NAVI_PROVIDER_LABEL: Record<NaviProvider, string> = {
  amap: '高德地图',
  baidu: '百度地图',
  qqmap: '腾讯地图'
}

const NAVI_PROVIDER_KEY = 'navi_provider'

/** 记住用户上次选的地图，下次默认沿用（仍可切换） */
export function getPreferredProvider(): NaviProvider {
  const v = storage.get<string>(NAVI_PROVIDER_KEY, '')
  return v === 'amap' || v === 'baidu' || v === 'qqmap' ? v : 'amap'
}

export function setPreferredProvider(p: NaviProvider): void {
  storage.set(NAVI_PROVIDER_KEY, p)
}

/** 坐标格式：经度在前（高德 URI 的约定），百度用 纬度,经度 */
const amapPoint = (p: NaviPoint) => `${p.lng},${p.lat},${encodeURIComponent(p.name)}`
const baiduPoint = (p: NaviPoint) => `latlng:${p.lat},${p.lng}|name:${encodeURIComponent(p.name)}`

/**
 * 高德：带起点的路线（Web，移动端会按 callnative 尝试唤起 App）
 *
 * 注意 via 限制：高德 URI 的 via 最多 1 个途经点且仅驾车模式。
 * 所以这里只拼 from + to —— 全天多段路线必须在应用内逐段唤起，不能指望一次导入。
 */
export function buildAmapRouteUrl(from: NaviPoint, to: NaviPoint, mode: NaviMode = 'drive'): string {
  const qs = [
    `from=${amapPoint(from)}`,
    `to=${amapPoint(to)}`,
    `mode=${AMAP_WEB_MODE[mode]}`,
    'policy=1',
    `src=${encodeURIComponent('WanderNote')}`,
    'coordinate=gaode',
    'callnative=1'
  ].join('&')
  return `https://uri.amap.com/navigation?${qs}`
}

/** 高德 App scheme（带起点，坐标必须是 GCJ-02） */
export function buildAmapRouteScheme(from: NaviPoint, to: NaviPoint, mode: NaviMode = 'drive'): string {
  return (
    `amapuri://route/plan/?sourceApplication=${encodeURIComponent('WanderNote')}` +
    `&slat=${from.lat}&slon=${from.lng}&sname=${encodeURIComponent(from.name)}` +
    `&dlat=${to.lat}&dlon=${to.lng}&dname=${encodeURIComponent(to.name)}` +
    `&dev=0&t=${AMAP_APP_MODE[mode]}`
  )
}

/**
 * 百度：带起点的路线（Web 兜底）
 *
 * coord_type=gcj02 必须有：我们的坐标来自腾讯位置服务（GCJ-02），
 * 百度默认按 BD-09 解析，不声明会导致偏移几百米。
 */
export function buildBaiduRouteUrl(from: NaviPoint, to: NaviPoint, mode: NaviMode = 'drive'): string {
  const qs = [
    `origin=${baiduPoint(from)}`,
    `destination=${baiduPoint(to)}`,
    `mode=${mode === 'walk' ? 'walking' : mode === 'bus' ? 'transit' : mode === 'bike' ? 'riding' : 'driving'}`,
    'coord_type=gcj02',
    'output=html',
    `src=${encodeURIComponent('webapp.wandernote')}`
  ].join('&')
  return `https://api.map.baidu.com/direction?${qs}`
}

/** 百度 App scheme */
export function buildBaiduScheme(from: NaviPoint, to: NaviPoint, mode: NaviMode = 'drive'): string {
  const qs = [
    `origin=${baiduPoint(from)}`,
    `destination=${baiduPoint(to)}`,
    `mode=${mode === 'walk' ? 'walking' : mode === 'bus' ? 'transit' : mode === 'bike' ? 'riding' : 'driving'}`,
    'coord_type=gcj02',
    `src=${encodeURIComponent('android.wandernote')}`
  ].join('&')
  return `baidumap://map/direction?${qs}`
}

/** 腾讯：带起点的路线（web/App 同一个 URI，装了 App 会自动唤起） */
export function buildQqMapRouteUrl(from: NaviPoint, to: NaviPoint, mode: NaviMode = 'drive'): string {
  const qs = [
    `type=${mode}`,
    `from=${encodeURIComponent(from.name)}`,
    `fromcoord=${from.lat},${from.lng}`,
    `to=${encodeURIComponent(to.name)}`,
    `tocoord=${to.lat},${to.lng}`,
    `referer=${encodeURIComponent('WanderNote')}`
  ].join('&')
  return `https://apis.map.qq.com/uri/v1/routeplan?${qs}`
}

/** 腾讯 App scheme */
export function buildQqMapRouteScheme(from: NaviPoint, to: NaviPoint, mode: NaviMode = 'drive'): string {
  const qs = [
    `type=${mode}`,
    `from=${encodeURIComponent(from.name)}`,
    `fromcoord=${from.lat},${from.lng}`,
    `to=${encodeURIComponent(to.name)}`,
    `tocoord=${to.lat},${to.lng}`,
    `referer=${encodeURIComponent('WanderNote')}`
  ].join('&')
  return `qqmap://map/routeplan?${qs}`
}

/**
 * 唤起外部地图导航某一段路。
 *
 * App 端先探测是否安装，装了用 scheme 直达、没装走网页版；
 * H5 直接开网页版（装了对应 App 会自动拉起）；
 * 小程序等无法唤起的环境退化为复制链接。
 */
export function openRouteNavi(
  provider: NaviProvider,
  from: NaviPoint,
  to: NaviPoint,
  mode: NaviMode = 'drive'
): void {
  const webUrl =
    provider === 'amap'
      ? buildAmapRouteUrl(from, to, mode)
      : provider === 'baidu'
        ? buildBaiduRouteUrl(from, to, mode)
        : buildQqMapRouteUrl(from, to, mode)

  // #ifdef APP-PLUS
  const app = provider === 'amap' ? AMAP_APP : provider === 'baidu' ? BAIDU_APP : QQMAP_APP
  const scheme =
    provider === 'amap'
      ? buildAmapRouteScheme(from, to, mode)
      : provider === 'baidu'
        ? buildBaiduScheme(from, to, mode)
        : buildQqMapRouteScheme(from, to, mode)
  if (appInstalled(app)) {
    plus.runtime.openURL(scheme)
  } else {
    showToast({ title: `未检测到${NAVI_PROVIDER_LABEL[provider]}，已打开网页版`, icon: 'none' })
    plus.runtime.openURL(webUrl)
  }
  // #endif
  // #ifdef H5
  window.open(webUrl, '_blank')
  // #endif
  // #ifndef H5 || APP-PLUS
  copyLinkFallback(webUrl)
  // #endif
}

/**
 * 让用户选一家地图（并记住选择）。
 * @returns 选中的地图；用户取消返回 null
 */
export function chooseNaviProvider(): Promise<NaviProvider | null> {
  return new Promise((resolve) => {
    showActionSheet({
      itemList: ['高德地图', '百度地图', '腾讯地图'],
      success: (res) => {
        const picked: NaviProvider[] = ['amap', 'baidu', 'qqmap']
        const p = picked[res.tapIndex]
        if (p) setPreferredProvider(p)
        resolve(p ?? null)
      },
      fail: () => resolve(null)
    })
  })
}
