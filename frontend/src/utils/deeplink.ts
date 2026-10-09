/**
 * 第三方深链封装（携程酒店 / 12306 火车票 / 携程机票；高德/百度/腾讯导航仍在 utils/map.ts）。
 *
 * 为什么要单独一层：
 *   携程与 12306 都没有公开 API，交易只能靠 H5/App 深链跳转，而参数名会随它们前端改版而变。
 *   这里和后端的 CtripUrlBuilder / TicketUrlBuilder 是**同一套模板 + 同样的占位符**：
 *     · 正常链路：后端 /hotel/search、/ticket/search 每条候选直接返回深链，前端原样打开；
 *     · 离线/接口挂掉/该线路未收录：前端按下面的模板自己拼一条，保证按钮仍然可用。
 *   模板改版时两边一起改（后端在 application.yml 的 ctrip.hotel / ticket.train / ticket.flight）。
 *
 * ★ 参数模板以官方 URL 生成工具产出的结果为准 ★
 *   携程：http://pages.ctrip.com/commerce/promote/common/tools/converturl/index.html
 *   12306：车票查询页 URL 格式非官方承诺，随时可能变 —— 所以拼不出站名时退化为官网首页。
 *   下面这几份都是可用近似，上线前真机各验证一次。
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

// ==================== 订票（火车票 / 飞机票） ====================

/** 与后端 TicketUrlBuilder.DEFAULT_TRAIN_TEMPLATE 保持一致（12306 车票查询页） */
export const TICKET_TRAIN_URL_TEMPLATE =
  'https://kyfw.12306.cn/otn/leftTicket/init?linktypeid=dc&fs={from}&ts={to}&date={date}&flag=N,N,Y'

/** 与后端 TicketUrlBuilder.DEFAULT_FLIGHT_TEMPLATE 保持一致（携程 H5 机票搜索） */
export const TICKET_FLIGHT_URL_TEMPLATE =
  'https://m.ctrip.com/webapp/flight/search?dcity={fromCity}&acity={toCity}&ddate={date}'

/** 拼不出站名时的官网兜底（永远可用，不会给人一条搜不出东西的链接） */
export const TICKET_TRAIN_HOME = 'https://www.12306.cn/index/'
export const TICKET_FLIGHT_HOME = 'https://m.ctrip.com/webapp/flight'

export interface TicketUrlParams {
  /**
   * train=火车票 flight=飞机票 drive=自驾。
   * 自驾是「无班次方式」，没有可查的查询页 —— buildTicketUrl 对它直接返回 null，
   * 调用方（按钮）应当隐藏，而不是给一条搜不出东西的链接。
   */
  type?: 'train' | 'flight' | 'drive'
  /** 出发站/机场；为空时用城市名 */
  fromStation?: string
  toStation?: string
  /** 出发/到达城市 */
  fromCity?: string
  toCity?: string
  /** 出发日期 YYYY-MM-DD */
  date?: string
  /** 车次号/航班号 */
  ticketNo?: string
}

/**
 * 拼订票深链（12306 / 携程机票）。
 *
 * @returns 深链；自驾等无班次方式返回 null；出发与到达都为空时返回对应官网首页 ——
 *          与后端 TicketUrlBuilder 的降级行为一致，保证「有班次」时按钮永远有链接。
 *
 * ★ 合规：这里只生成「查询页」链接，绝不拼下单/支付参数，也不带任何用户凭证。
 */
export function buildTicketUrl(params: TicketUrlParams): string | null {
  // 无班次方式（自驾）：没有车次可查，也就不该有按钮
  if (params.type === 'drive') return null
  const flight = params.type === 'flight'
  const from = (params.fromStation || params.fromCity || '').trim()
  const to = (params.toStation || params.toCity || '').trim()

  if (!from && !to) {
    return flight ? TICKET_FLIGHT_HOME : TICKET_TRAIN_HOME
  }

  const template = flight ? TICKET_FLIGHT_URL_TEMPLATE : TICKET_TRAIN_URL_TEMPLATE
  let url = template
    .replace('{from}', encodeParam(from))
    .replace('{to}', encodeParam(to))
    .replace('{fromCity}', encodeParam(params.fromCity))
    .replace('{toCity}', encodeParam(params.toCity))
    .replace('{date}', encodeParam(params.date))
    .replace('{no}', encodeParam(params.ticketNo))

  // 没被填上的可选参数会留下 "date=&no="，清掉让链接干净些
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

/**
 * 打开发往 12306 / 携程的购票页。
 *
 * @param url       后端返回的深链（优先）
 * @param fallback  后端没给时用本地模板现场拼一条
 */
export function openTicketPurchase(url: string | undefined, fallback: TicketUrlParams): void {
  // 后端深链优先；没有就用本地模板拼。无班次方式（自驾）拼出来是 null，
  // 这里与 openCtripHotel 一样走到「暂无可用的预订链接」，而不是打开一个空地址。
  const target = (url || '').trim() || buildTicketUrl(fallback)
  if (!target) {
    showToast({ title: '该方式没有可查询的购票页', icon: 'none' })
    return
  }
  openExternalUrl(target)
}
