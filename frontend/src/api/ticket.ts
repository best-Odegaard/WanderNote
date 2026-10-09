/**
 * 订票（火车票 / 飞机票）接口。
 *
 * 边界：本站只做「候选班次筛选 + 跳转 12306 / 携程深链」，不做站内下单、不代购。
 *   12306 与航司都没有公开开放 API，price 是**参考价**，接口里没有余票/舱位字段 ——
 *   展示时必须带「以 12306 / 携程为准」，禁止出现「有票」「可订」字样。
 *
 * 合规红线（与后端 TicketController 的约定一致）：
 *   本文件不传、不存任何购票凭证（证件号 / 手机号 / 12306 账号），
 *   接口里也刻意没有这些字段 —— 将来也不许加。
 */
import http from '@/utils/request'

/** 票种：火车票 / 飞机票（只有这两种能查候选班次） */
export type TicketType = 'train' | 'flight'

/**
 * 交通方式：行程里能记录的三种。
 *
 * 比 TicketType 多一个 `drive`（自驾）—— 它是「无班次方式」：
 * 没有车次号、没有席别、没有 12306/携程查询页，purchaseUrl 为空，
 * 靠 durationMin + distanceKm + price（都是估算值）表达「多久、多远、大概多少钱」。
 */
export type TransportMode = TicketType | 'drive'

/** 方向：去程 / 返程。「单程」不是第三种方向，而是只有 outbound。 */
export type TicketDirection = 'outbound' | 'return'

/** 后端 TicketVO 的结构 */
export interface TicketItem {
  /** 业务编码（ticket_candidate.ticket_code）；兜底班次与自驾没有编码 */
  id?: string
  direction?: TicketDirection
  transportType?: TransportMode
  /** 承运方（铁路局 / 航空公司）；自驾为空 */
  carrier?: string
  /** 车次号（C7001）或航班号（CZ3101）；自驾为空 */
  ticketNo?: string
  fromCity?: string
  toCity?: string
  /** 出发站/机场 */
  fromStation?: string
  /** 到达站/机场 */
  toStation?: string
  /** 出发日期 YYYY-MM-DD，可空 */
  departDate?: string
  /** 出发时刻 HH:mm；自驾为空 */
  departTime?: string
  /** 到达时刻 HH:mm；自驾为空 */
  arriveTime?: string
  /** 历时（分钟） */
  durationMin?: number
  /** 里程（公里）：地图规划值或直线估算 */
  distanceKm?: number
  /** 席别/舱位（二等座 / 经济舱）；自驾为空 */
  seatClass?: string
  /** 参考价（元/人），以 12306 或携程为准；自驾为估算油费+过路费 */
  price?: number
  /** 经停次数，0=直达 */
  stops?: number
  tags?: string[]
  /** 购买深链；为空时前端按同一套模板现拼（utils/deeplink.ts）；自驾恒为空 */
  purchaseUrl?: string
  /** 仅 GET /ticket/trip/{tripId} 与 /ticket/select 会填 */
  tripId?: number
}

export interface TicketSearchParams {
  /** train=火车票 flight=飞机票；不传则不限票种 */
  type?: TicketType
  from?: string
  to?: string
  /** YYYY-MM-DD，仅用于拼深链 */
  date?: string
}

/**
 * 选定班次 / 交通方式：优先用 ticketCode；兜底班次（库里没有的线路）才传其余字段。
 * 自驾（transportType='drive'）不需要 ticketCode 与 ticketNo，
 * 但要传 durationMin / distanceKm / price（估算值）。
 */
export interface TicketSelectParams {
  /** 行程id；行程还没保存时留空（后端只校验不落库） */
  tripId?: number | string
  direction: TicketDirection
  ticketCode?: string
  transportType?: TransportMode
  carrier?: string
  ticketNo?: string
  fromCity?: string
  toCity?: string
  fromStation?: string
  toStation?: string
  departDate?: string
  departTime?: string
  arriveTime?: string
  durationMin?: number
  distanceKm?: number
  seatClass?: string
  price?: number
}

/** 候选班次 — GET /ticket/search */
export function searchTickets(params: TicketSearchParams) {
  return http.get<TicketItem[]>('/ticket/search', {
    type: params.type,
    from: params.from,
    to: params.to,
    date: params.date
  })
}

/** 选定班次（写 trip_ticket）— POST /ticket/select */
export function selectTicket(data: TicketSelectParams) {
  return http.post<TicketItem>('/ticket/select', data)
}

/** 读行程已选的票（去程在前、返程在后；没有则返回空数组） — GET /ticket/trip/:tripId */
export function getTripTickets(tripId: number | string) {
  return http.get<TicketItem[]>(`/ticket/trip/${tripId}`)
}

/** 清除行程的票（direction 为空则去程与返程都清） — POST /ticket/clear */
export function clearTripTicket(tripId: number | string, direction?: TicketDirection) {
  const query = direction ? `?tripId=${tripId}&direction=${direction}` : `?tripId=${tripId}`
  return http.post<void>(`/ticket/clear${query}`)
}
