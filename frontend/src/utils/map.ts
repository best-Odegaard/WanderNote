/**
 * 地图导航工具 — 轻量方案
 * 不内嵌地图、不做地理编码，直接拼第三方地图导航链接，跳转外部地图 App/网页。
 * 导航交互：点击后弹出三选项（复制地址 / 高德导航 / 腾讯导航）。
 * - H5：浏览器无法检测手机是否安装地图 App，直接打开"优先唤起 App"的网页链接，
 *   装了对应 App 会自动拉起进入导航，未安装则展示网页版导航（效果等同）。
 * - App：用 plus.runtime.isApplicationExist 检测高德/腾讯地图是否安装，
 *   已安装用 App scheme 直接唤起，未安装提示并打开网页版兜底。
 */

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
    success: () => uni.showToast({ title: '导航链接已复制，请到浏览器打开', icon: 'none' })
  })
}

/** 打开高德导航：App 端先检测安装再唤起，H5 打开唤起式链接 */
export function openAmapNavi(target: NaviTarget): void {
  const url = buildAmapNaviUrl(target.title, target.address || '', target.mode)
  // #ifdef APP-PLUS
  if (appInstalled(AMAP_APP)) {
    plus.runtime.openURL(buildAmapScheme(target))
  } else {
    uni.showToast({ title: '未检测到高德地图，已打开网页版', icon: 'none' })
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
    uni.showToast({ title: '未检测到腾讯地图，已打开网页版', icon: 'none' })
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
    success: () => uni.showToast({ title: '地址已复制', icon: 'success' })
  })
}

/**
 * 点击"导航"入口：弹出三选项
 * 1) 复制地址信息
 * 2) 高德地图导航（App 检测安装后唤起；H5 唤起式链接）
 * 3) 腾讯地图导航（同上）
 */
export function showNaviOptions(target: NaviTarget): void {
  uni.showActionSheet({
    itemList: ['复制地址信息', '高德地图导航', '腾讯地图导航'],
    success: (res) => {
      if (res.tapIndex === 0) copyAddress(target)
      else if (res.tapIndex === 1) openAmapNavi(target)
      else if (res.tapIndex === 2) openQqMapNavi(target)
    }
  })
}
