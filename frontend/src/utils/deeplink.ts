/**
 * 第三方深链封装（当前只有携程酒店；高德/百度/腾讯导航仍在 utils/map.ts）。
 *
 * 为什么要单独一层：
 *   携程没有公开 API，交易只能靠 H5/App 深链跳转，而参数名会随携程前端改版而变。
 *   这里和后端 com.gkv.utils.CtripUrlBuilder 是**同一套模板 + 同样的占位符**：
 *     · 正常链路：后端 /hotel/search 每条候选直接返回 ctripUrl，前端原样打开；
 *     · 离线/接口挂掉：前端按下面的模板自己拼一条，保证「携程预订」按钮仍然可用。
 *   模板改版时两边一起改（后端在 application.yml 的 ctrip.hotel.url-template）。
 *
 * ★ 参数模板以携程官方 URL 生成工具产出的结果为准 ★
 *   http://pages.ctrip.com/commerce/promote/common/tools/converturl/index.html
 *   下面这份是可用近似，上线前真机验证一次。
 */
import { showToast } from '@/utils/feedback'

/** 与后端 CtripUrlBuilder.DEFAULT_TEMPLATE 保持一致 */
export const CTRIP_HOTEL_URL_TEMPLATE =
  'https://m.ctrip.com/webapp/hotel/hotellist?cityName={city}&checkin={checkin}&checkout={checkout}&keyword={keyword}'

export interface CtripHotelParams {
  city?: string
  /** YYYY-MM-DD */
  checkin?: string
  checkout?: string
  /** 酒店名 */
  hotelName?: string
  /** 附近地标（用户要求「地点和酒店名一起搜」） */
  landmark?: string
}

/** 用 %20 而不是 '+'：'+' 在部分解析路径里会被当成字面加号，城市名带空格时搜不到 */
function encodeParam(raw?: string): string {
  return encodeURIComponent((raw || '').trim()).replace(/%20/g, '%20')
}

/**
 * 拼携程酒店深链。
 *
 * @returns 深链；城市与关键词都为空时返回 null —— 调用方应隐藏「携程预订」按钮，
 *          给一条搜不到东西的链接比不给更伤（与后端行为一致）。
 */
export function buildCtripHotelUrl(params: CtripHotelParams): string | null {
  const city = (params.city || '').trim()
  const name = (params.hotelName || '').trim()
  const landmark = (params.landmark || '').trim()
  const keyword = [name, landmark].filter(Boolean).join(' ')

  if (!city && !keyword) return null

  let url = CTRIP_HOTEL_URL_TEMPLATE
    .replace('{city}', encodeParam(city))
    .replace('{checkin}', encodeParam(params.checkin))
    .replace('{checkout}', encodeParam(params.checkout))
    .replace('{keyword}', encodeParam(keyword))

  // 没被填上的可选参数会留下 "checkin=&checkout="，清掉让链接干净些
  const [head, query] = url.split('?')
  if (query) {
    const kept = query
      .split('&')
      .filter((pair) => pair && !pair.endsWith('='))
      .join('&')
    url = kept ? `${head}?${kept}` : head
  }
  return url
}

/** 打开外部链接的兜底：App 端打不开就复制，H5 被拦截就同窗跳 */
function copyLinkFallback(url: string) {
  uni.setClipboardData({
    data: url,
    success: () => showToast({ title: '预订链接已复制，请到浏览器打开', icon: 'none' })
  })
}

/**
 * 打开一个外部网页链接（携程 H5 这类）。
 *
 * - App：plus.runtime.openURL，失败（无浏览器/系统拦截）则复制链接；
 * - H5：window.open 新窗口；被弹窗拦截时退化为同窗跳转，保证一定跳得走。
 */
export function openExternalUrl(url: string): void {
  if (!url) {
    showToast({ title: '暂无可用的预订链接', icon: 'none' })
    return
  }
  // #ifdef APP-PLUS
  plus.runtime.openURL(url, () => copyLinkFallback(url))
  // #endif
  // #ifdef H5
  const opened = window.open(url, '_blank')
  if (!opened) {
    // 弹窗被拦截（非用户手势触发/浏览器策略）：同窗跳转，别让用户点了没反应
    window.location.href = url
  }
  // #endif
  // #ifndef H5 || APP-PLUS
  copyLinkFallback(url)
  // #endif
}

/**
 * 打开携程酒店预订页。
 *
 * @param url       后端返回的深链（优先）
 * @param fallback  后端没给时用本地模板现场拼一条
 */
export function openCtripHotel(url: string | undefined, fallback: CtripHotelParams): void {
  const target = (url || '').trim() || buildCtripHotelUrl(fallback)
  if (!target) {
    showToast({ title: '暂无可用的预订链接', icon: 'none' })
    return
  }
  openExternalUrl(target)
}
