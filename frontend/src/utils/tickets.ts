/**
 * 订票（火车票 / 飞机票）候选数据。
 *
 * 数据来源分两层（与 utils/hotels.ts 完全同一套做法，这是刻意的）：
 *   1) 首选后端 `GET /ticket/search`（ticket_candidate 表，运营可维护、可加线路不发版）；
 *   2) 接口不可用/该线路未收录时，用手写的本地候选表兜底。
 *      「怎么去、怎么回」是行程的一部分，接口挂了不能连票都选不了。
 *
 * 两层用**同一套 ticket_code**（如 train-gz-zq-c7001）：
 *   断网时用户选的班次，联网后仍能按 code 对上号（trip_ticket.ticket_code）。
 *
 * 与酒店那套的关键差异（别照抄酒店的经验做错）：
 *   酒店能对未知城市返回「XX市中心便捷酒店」这类通用候选，因为「住一晚」到哪都成立；
 *   「广州 → 肇庆」的车次放到别的线路上就是错的。所以这里候选查不到时返回**空列表**，
 *   由页面降级成「去 12306 / 携程查」按钮 —— 宁可少给，也不给错的。
 *
 * 价格一律是**参考价**，展示必须带「以 12306 / 携程为准」——
 * 本站没有余票/舱位，也不做站内下单与代购。
 */
import {
  searchTickets,
  type TicketItem,
  type TicketType,
  type TicketDirection,
  type TransportMode
} from '@/api/ticket'
import { USE_MOCK } from './constant'

export type { TicketType, TicketDirection, TransportMode }

/** 有班次可查的票种展示文案 */
export const TICKET_TYPE_LABEL: Record<TicketType, string> = {
  train: '火车',
  flight: '飞机'
}

/**
 * 交通方式展示文案（含自驾这类无班次方式）。
 *
 * 为什么单独一张表而不是复用票种文案：自驾不是「票」，
 * 卡片上要写「自驾」而不是「火车/飞机」，两者不能混。
 */
export const TRANSPORT_MODE_LABEL: Record<TransportMode, string> = {
  train: '高铁/动车',
  flight: '飞机',
  drive: '自驾'
}

/** 方式图标（交通卡与推荐行都用它） */
export const TRANSPORT_MODE_EMOJI: Record<TransportMode, string> = {
  train: '🚄',
  flight: '✈️',
  drive: '🚗'
}

/** 方向展示文案 */
export const TICKET_DIRECTION_LABEL: Record<TicketDirection, string> = {
  outbound: '去程',
  return: '返程'
}

/** 把后端/接口来的字符串收敛成合法方式（未知值一律当火车票，绝不返回 undefined） */
export function normalizeMode(raw?: string): TransportMode {
  return raw === 'flight' || raw === 'drive' ? raw : 'train'
}

export interface TicketOption {
  /** 业务编码（与后端 ticket_code 同源）；兜底班次与自驾用本地拼的 id */
  id: string
  /** 交通方式：train / flight / drive（自驾无班次、无购买链接） */
  transportType: TransportMode
  carrier: string
  /** 车次号 / 航班号；自驾为空 */
  ticketNo: string
  fromCity: string
  toCity: string
  /** 出发站 / 机场；自驾时退化为城市名 */
  fromStation: string
  toStation: string
  /** 出发日期 YYYY-MM-DD，可空 */
  departDate?: string
  /** 出发 / 到达时刻 HH:mm；自驾为空 */
  departTime: string
  arriveTime: string
  /** 历时（分钟） */
  durationMin: number
  /** 里程（公里）：地图规划值或直线估算 */
  distanceKm?: number
  /** 席别 / 舱位；自驾为空 */
  seatClass: string
  /** 参考价（元/人）或自驾的估算油费+过路费，以 12306 或携程为准 */
  price: number
  /** 经停次数，0 = 直达 */
  stops: number
  tags: string[]
  /** 购买深链；后端优先，本地兜底由 utils/deeplink.ts 现场拼；自驾恒为空 */
  purchaseUrl?: string
  direction?: TicketDirection
}

/** 一行候选的紧凑写法：本地表条目多，逐字段展开读起来反而更糊 */
interface Seed {
  id: string
  type: TicketType
  carrier: string
  no: string
  from: string
  to: string
  fromStation: string
  toStation: string
  dep: string
  arr: string
  /** 历时（分钟） */
  dur: number
  seat: string
  price: number
  stops?: number
  tags: string[]
}

/**
 * 本地候选表 —— 与 `部署表结构/V2.8__create_ticket.sql` 的种子数据一一对应。
 * 只覆盖热门线路；其余线路由页面降级为「去 12306 / 携程查」。
 */
const TICKETS: Seed[] = [
  // ── 广州 ↔ 肇庆 ──
  { id: 'train-gz-zq-c7001', type: 'train', carrier: '广铁集团', no: 'C7001', from: '广州', to: '肇庆', fromStation: '广州南', toStation: '肇庆东', dep: '07:12', arr: '07:47', dur: 35, seat: '二等座', price: 26, tags: ['早班', '直达'] },
  { id: 'train-gz-zq-c7015', type: 'train', carrier: '广铁集团', no: 'C7015', from: '广州', to: '肇庆', fromStation: '广州南', toStation: '肇庆东', dep: '09:30', arr: '10:06', dur: 36, seat: '二等座', price: 26, tags: ['直达'] },
  { id: 'train-gz-zq-d1832', type: 'train', carrier: '广铁集团', no: 'D1832', from: '广州', to: '肇庆', fromStation: '广州南', toStation: '肇庆东', dep: '14:05', arr: '14:49', dur: 44, seat: '二等座', price: 30, tags: ['下午班'] },
  { id: 'train-zq-gz-c7002', type: 'train', carrier: '广铁集团', no: 'C7002', from: '肇庆', to: '广州', fromStation: '肇庆东', toStation: '广州南', dep: '08:05', arr: '08:41', dur: 36, seat: '二等座', price: 26, tags: ['早班', '直达'] },
  { id: 'train-zq-gz-c7028', type: 'train', carrier: '广铁集团', no: 'C7028', from: '肇庆', to: '广州', fromStation: '肇庆东', toStation: '广州南', dep: '18:20', arr: '18:56', dur: 36, seat: '二等座', price: 26, tags: ['晚班返程'] },

  // ── 广州 ↔ 成都 ──
  { id: 'train-gz-cd-g3702', type: 'train', carrier: '广铁集团', no: 'G3702', from: '广州', to: '成都', fromStation: '广州南', toStation: '成都东', dep: '08:16', arr: '17:02', dur: 526, seat: '二等座', price: 764, tags: ['高铁', '直达'] },
  { id: 'train-gz-cd-z122', type: 'train', carrier: '广铁集团', no: 'Z122', from: '广州', to: '成都', fromStation: '广州', toStation: '成都东', dep: '19:32', arr: '06:12', dur: 1600, seat: '硬卧', price: 428, stops: 2, tags: ['夕发朝至', '省一晚住宿'] },
  { id: 'flight-gz-cd-cz3401', type: 'flight', carrier: '南方航空', no: 'CZ3401', from: '广州', to: '成都', fromStation: '白云机场T2', toStation: '天府机场T1', dep: '07:35', arr: '10:05', dur: 150, seat: '经济舱', price: 780, tags: ['早班', '含餐'] },
  { id: 'flight-gz-cd-3u8732', type: 'flight', carrier: '四川航空', no: '3U8732', from: '广州', to: '成都', fromStation: '白云机场T1', toStation: '双流机场T2', dep: '15:20', arr: '17:55', dur: 155, seat: '经济舱', price: 640, tags: ['下午班'] },
  { id: 'train-cd-gz-g3701', type: 'train', carrier: '成都局集团', no: 'G3701', from: '成都', to: '广州', fromStation: '成都东', toStation: '广州南', dep: '07:58', arr: '16:44', dur: 526, seat: '二等座', price: 764, tags: ['高铁', '直达'] },
  { id: 'flight-cd-gz-cz3402', type: 'flight', carrier: '南方航空', no: 'CZ3402', from: '成都', to: '广州', fromStation: '天府机场T1', toStation: '白云机场T2', dep: '19:40', arr: '22:05', dur: 145, seat: '经济舱', price: 720, tags: ['晚班返程'] },

  // ── 广州 ↔ 杭州 ──
  { id: 'train-gz-hz-g86', type: 'train', carrier: '广铁集团', no: 'G86', from: '广州', to: '杭州', fromStation: '广州南', toStation: '杭州东', dep: '08:00', arr: '14:02', dur: 362, seat: '二等座', price: 682, tags: ['高铁', '直达'] },
  { id: 'flight-gz-hz-ca1726', type: 'flight', carrier: '中国国航', no: 'CA1726', from: '广州', to: '杭州', fromStation: '白云机场T2', toStation: '萧山机场T3', dep: '09:15', arr: '11:20', dur: 125, seat: '经济舱', price: 560, tags: ['上午班'] },
  { id: 'flight-gz-hz-mu5392', type: 'flight', carrier: '东方航空', no: 'MU5392', from: '广州', to: '杭州', fromStation: '白云机场T1', toStation: '萧山机场T4', dep: '18:30', arr: '20:35', dur: 125, seat: '经济舱', price: 480, tags: ['晚班'] },
  { id: 'train-hz-gz-g85', type: 'train', carrier: '上海局集团', no: 'G85', from: '杭州', to: '广州', fromStation: '杭州东', toStation: '广州南', dep: '09:10', arr: '15:12', dur: 362, seat: '二等座', price: 682, tags: ['高铁', '直达'] },
  { id: 'flight-hz-gz-ca1725', type: 'flight', carrier: '中国国航', no: 'CA1725', from: '杭州', to: '广州', fromStation: '萧山机场T3', toStation: '白云机场T2', dep: '20:05', arr: '22:15', dur: 130, seat: '经济舱', price: 520, tags: ['晚班返程'] },

  // ── 广州 ↔ 重庆 ──
  { id: 'train-gz-cq-d1852', type: 'train', carrier: '广铁集团', no: 'D1852', from: '广州', to: '重庆', fromStation: '广州南', toStation: '重庆西', dep: '09:12', arr: '17:36', dur: 504, seat: '二等座', price: 553, tags: ['动车', '直达'] },
  { id: 'flight-gz-cq-cz3461', type: 'flight', carrier: '南方航空', no: 'CZ3461', from: '广州', to: '重庆', fromStation: '白云机场T2', toStation: '江北机场T3', dep: '11:20', arr: '13:30', dur: 130, seat: '经济舱', price: 590, tags: ['午班'] },
  { id: 'flight-gz-cq-pn6207', type: 'flight', carrier: '西部航空', no: 'PN6207', from: '广州', to: '重庆', fromStation: '白云机场T1', toStation: '江北机场T2', dep: '21:05', arr: '23:15', dur: 130, seat: '经济舱', price: 420, tags: ['红眼', '便宜'] },
  { id: 'train-cq-gz-d1851', type: 'train', carrier: '成都局集团', no: 'D1851', from: '重庆', to: '广州', fromStation: '重庆西', toStation: '广州南', dep: '08:26', arr: '16:50', dur: 504, seat: '二等座', price: 553, tags: ['动车', '直达'] },
  { id: 'flight-cq-gz-cz3462', type: 'flight', carrier: '南方航空', no: 'CZ3462', from: '重庆', to: '广州', fromStation: '江北机场T3', toStation: '白云机场T2', dep: '14:40', arr: '16:50', dur: 130, seat: '经济舱', price: 610, tags: ['下午返程'] },

  // ── 广州 ↔ 桂林 ──
  { id: 'train-gz-gl-d2812', type: 'train', carrier: '广铁集团', no: 'D2812', from: '广州', to: '桂林', fromStation: '广州南', toStation: '桂林北', dep: '08:32', arr: '11:14', dur: 162, seat: '二等座', price: 152.5, tags: ['动车', '直达'] },
  { id: 'train-gz-gl-g2918', type: 'train', carrier: '广铁集团', no: 'G2918', from: '广州', to: '桂林', fromStation: '广州南', toStation: '桂林西', dep: '15:10', arr: '17:26', dur: 136, seat: '二等座', price: 168, tags: ['高铁', '下午班'] },
  { id: 'train-gl-gz-d2811', type: 'train', carrier: '南宁局集团', no: 'D2811', from: '桂林', to: '广州', fromStation: '桂林北', toStation: '广州南', dep: '17:05', arr: '19:47', dur: 162, seat: '二等座', price: 152.5, tags: ['晚班返程'] },

  // ── 广州 ↔ 北京 ──
  { id: 'train-gz-bj-g66', type: 'train', carrier: '广铁集团', no: 'G66', from: '广州', to: '北京', fromStation: '广州南', toStation: '北京西', dep: '08:00', arr: '15:59', dur: 479, seat: '二等座', price: 862, tags: ['高铁', '直达'] },
  { id: 'flight-gz-bj-ca1316', type: 'flight', carrier: '中国国航', no: 'CA1316', from: '广州', to: '北京', fromStation: '白云机场T2', toStation: '首都机场T3', dep: '08:30', arr: '11:45', dur: 195, seat: '经济舱', price: 1180, tags: ['早班'] },
  { id: 'flight-gz-bj-mu6300', type: 'flight', carrier: '东方航空', no: 'MU6300', from: '广州', to: '北京', fromStation: '白云机场T1', toStation: '大兴机场', dep: '19:00', arr: '22:15', dur: 195, seat: '经济舱', price: 890, tags: ['晚班'] },
  { id: 'train-bj-gz-g65', type: 'train', carrier: '北京局集团', no: 'G65', from: '北京', to: '广州', fromStation: '北京西', toStation: '广州南', dep: '09:00', arr: '16:59', dur: 479, seat: '二等座', price: 862, tags: ['高铁', '直达'] },
  { id: 'flight-bj-gz-ca1315', type: 'flight', carrier: '中国国航', no: 'CA1315', from: '北京', to: '广州', fromStation: '首都机场T3', toStation: '白云机场T2', dep: '13:20', arr: '16:40', dur: 200, seat: '经济舱', price: 1050, tags: ['下午返程'] },

  // ── 广州 ↔ 上海 ──
  { id: 'train-gz-sh-g100', type: 'train', carrier: '广铁集团', no: 'G100', from: '广州', to: '上海', fromStation: '广州南', toStation: '上海虹桥', dep: '08:05', arr: '15:04', dur: 419, seat: '二等座', price: 793, tags: ['高铁', '直达'] },
  { id: 'flight-gz-sh-mu5301', type: 'flight', carrier: '东方航空', no: 'MU5301', from: '广州', to: '上海', fromStation: '白云机场T1', toStation: '虹桥机场T2', dep: '09:40', arr: '12:00', dur: 140, seat: '经济舱', price: 690, tags: ['上午班'] },
  { id: 'flight-gz-sh-ho1252', type: 'flight', carrier: '吉祥航空', no: 'HO1252', from: '广州', to: '上海', fromStation: '白云机场T2', toStation: '浦东机场T1', dep: '20:10', arr: '22:30', dur: 140, seat: '经济舱', price: 520, tags: ['晚班'] },
  { id: 'train-sh-gz-g99', type: 'train', carrier: '上海局集团', no: 'G99', from: '上海', to: '广州', fromStation: '上海虹桥', toStation: '广州南', dep: '10:00', arr: '16:59', dur: 419, seat: '二等座', price: 793, tags: ['高铁', '直达'] },
  { id: 'flight-sh-gz-mu5302', type: 'flight', carrier: '东方航空', no: 'MU5302', from: '上海', to: '广州', fromStation: '虹桥机场T2', toStation: '白云机场T1', dep: '18:30', arr: '20:55', dur: 145, seat: '经济舱', price: 650, tags: ['晚班返程'] },

  // ── 广州 ↔ 西安 ──
  { id: 'train-gz-xa-g96', type: 'train', carrier: '广铁集团', no: 'G96', from: '广州', to: '西安', fromStation: '广州南', toStation: '西安北', dep: '08:55', arr: '16:20', dur: 445, seat: '二等座', price: 812.5, tags: ['高铁', '直达'] },
  { id: 'flight-gz-xa-mu2108', type: 'flight', carrier: '东方航空', no: 'MU2108', from: '广州', to: '西安', fromStation: '白云机场T1', toStation: '咸阳机场T3', dep: '10:25', arr: '12:55', dur: 150, seat: '经济舱', price: 620, tags: ['上午班'] },
  { id: 'train-xa-gz-g95', type: 'train', carrier: '西安局集团', no: 'G95', from: '西安', to: '广州', fromStation: '西安北', toStation: '广州南', dep: '09:30', arr: '16:55', dur: 445, seat: '二等座', price: 812.5, tags: ['高铁', '直达'] },
  { id: 'flight-xa-gz-mu2107', type: 'flight', carrier: '东方航空', no: 'MU2107', from: '西安', to: '广州', fromStation: '咸阳机场T3', toStation: '白云机场T1', dep: '17:40', arr: '20:15', dur: 155, seat: '经济舱', price: 580, tags: ['晚班返程'] },

  // ── 广州 ↔ 三亚 ──
  { id: 'flight-gz-sy-cz6741', type: 'flight', carrier: '南方航空', no: 'CZ6741', from: '广州', to: '三亚', fromStation: '白云机场T2', toStation: '凤凰机场', dep: '08:20', arr: '09:50', dur: 90, seat: '经济舱', price: 460, tags: ['早班', '快'] },
  { id: 'flight-gz-sy-hu7188', type: 'flight', carrier: '海南航空', no: 'HU7188', from: '广州', to: '三亚', fromStation: '白云机场T1', toStation: '凤凰机场', dep: '16:15', arr: '17:45', dur: 90, seat: '经济舱', price: 380, tags: ['下午班'] },
  { id: 'flight-sy-gz-cz6742', type: 'flight', carrier: '南方航空', no: 'CZ6742', from: '三亚', to: '广州', fromStation: '凤凰机场', toStation: '白云机场T2', dep: '20:30', arr: '22:00', dur: 90, seat: '经济舱', price: 420, tags: ['晚班返程'] },

  // ── 广州 ↔ 厦门 ──
  { id: 'train-gz-xm-d2388', type: 'train', carrier: '广铁集团', no: 'D2388', from: '广州', to: '厦门', fromStation: '广州东', toStation: '厦门北', dep: '08:47', arr: '13:05', dur: 258, seat: '二等座', price: 246.5, tags: ['动车', '直达'] },
  { id: 'flight-gz-xm-mf8302', type: 'flight', carrier: '厦门航空', no: 'MF8302', from: '广州', to: '厦门', fromStation: '白云机场T2', toStation: '高崎机场T3', dep: '12:10', arr: '13:30', dur: 80, seat: '经济舱', price: 400, tags: ['午班'] },
  { id: 'train-xm-gz-d2387', type: 'train', carrier: '南昌局集团', no: 'D2387', from: '厦门', to: '广州', fromStation: '厦门北', toStation: '广州东', dep: '14:20', arr: '18:38', dur: 258, seat: '二等座', price: 246.5, tags: ['下午返程'] },
  { id: 'flight-xm-gz-mf8301', type: 'flight', carrier: '厦门航空', no: 'MF8301', from: '厦门', to: '广州', fromStation: '高崎机场T3', toStation: '白云机场T2', dep: '19:40', arr: '21:05', dur: 85, seat: '经济舱', price: 430, tags: ['晚班返程'] }
]

/** 种子 → 页面用的 TicketOption */
function seedToOption(s: Seed): TicketOption {
  return {
    id: s.id,
    transportType: s.type,
    carrier: s.carrier,
    ticketNo: s.no,
    fromCity: s.from,
    toCity: s.to,
    fromStation: s.fromStation,
    toStation: s.toStation,
    departTime: s.dep,
    arriveTime: s.arr,
    durationMin: s.dur,
    seatClass: s.seat,
    price: s.price,
    stops: s.stops ?? 0,
    tags: s.tags
  }
}

/** 去掉行政区后缀，便于跨写法匹配（「广州市」≈「广州」） */
function normalizeCity(raw?: string): string {
  return (raw || '').trim().replace(/(特别行政区|自治区|市|省|区|县)$/, '')
}

/** 归一化后互相包含即算命中（与后端 TicketServiceImpl.cityMatches 一致） */
function cityMatches(a?: string, b?: string): boolean {
  const x = normalizeCity(a)
  const y = normalizeCity(b)
  if (!x || !y) return false
  return x === y || x.includes(y) || y.includes(x)
}

/**
 * 本地候选筛选（票种 + 出发/到达城市）。
 * 与后端一致：查不到就返回空列表，不硬凑无关班次。
 */
export function filterLocalTickets(type?: TicketType, from?: string, to?: string): TicketOption[] {
  if (!from || !to) return []
  return TICKETS.filter(
    (s) => (!type || s.type === type) && cityMatches(s.from, from) && cityMatches(s.to, to)
  ).map(seedToOption)
}

/**
 * 按出发时刻升序（早的在前）。
 *
 * 为什么默认按时刻而不是按价格：用户最先想知道的是「几点能走」；
 * 价格排序留给页面上的显式切换（sort=price）。
 */
export function sortByDepart(list: TicketOption[]): TicketOption[] {
  return [...list].sort((a, b) => a.departTime.localeCompare(b.departTime))
}

/** 按参考价升序 */
export function sortByPrice(list: TicketOption[]): TicketOption[] {
  return [...list].sort((a, b) => a.price - b.price)
}

/** 历时展示：`35分` / `8小时46分` */
export function formatDuration(min: number): string {
  if (!min || min <= 0) return '—'
  const h = Math.floor(min / 60)
  const m = min % 60
  if (h === 0) return `${m}分`
  return m === 0 ? `${h}小时` : `${h}小时${m}分`
}

/** 价格展示：`¥26` */
export function formatTicketPrice(price: number): string {
  return `¥${price}`
}

/** 里程展示：`120km` */
export function formatDistance(km: number): string {
  if (!km || km <= 0) return '—'
  return `${Math.round(km)}km`
}

/** 后端 TicketItem → 页面用的 TicketOption（补默认值，避免可选字段到处判空） */
export function toTicketOption(item: TicketItem): TicketOption {
  const mode = normalizeMode(item.transportType)
  return {
    id: item.id || `${mode}-${item.fromCity || ''}-${item.toCity || ''}-${item.ticketNo || ''}`,
    transportType: mode,
    carrier: item.carrier || '',
    ticketNo: item.ticketNo || '',
    fromCity: item.fromCity || '',
    toCity: item.toCity || '',
    fromStation: item.fromStation || item.fromCity || '',
    toStation: item.toStation || item.toCity || '',
    departDate: item.departDate,
    departTime: item.departTime || '',
    arriveTime: item.arriveTime || '',
    durationMin: item.durationMin ?? 0,
    distanceKm: item.distanceKm ?? undefined,
    seatClass: item.seatClass || '',
    price: item.price ?? 0,
    stops: item.stops ?? 0,
    tags: item.tags || [],
    purchaseUrl: item.purchaseUrl,
    direction: item.direction
  }
}

export interface TicketQuery {
  type?: TicketType
  from?: string
  to?: string
  /** YYYY-MM-DD，仅用于拼深链 */
  date?: string
  /** 强制只读本地兜底（接口明确不可用时） */
  localOnly?: boolean
}

export interface TicketQueryResult {
  list: TicketOption[]
  /** 数据来自接口（true）还是本地兜底（false）——页面据此决定要不要提示「离线候选」 */
  fromApi: boolean
}

/**
 * 取候选班次：优先接口，失败/为空则本地兜底。
 *
 * 为什么「接口返回空」也要兜底：
 *   ticket_candidate 是运营维护的表（V2.8 建表 + 种子数据）。
 *   若该 DDL 还没在某套环境执行，接口会 500、表为空 —— 这时不能把「选票」整步卡死，
 *   本地候选表仍能把「选票 → 加入行程」跑通，等表建好自动切到接口数据。
 */
export async function fetchTicketOptions(query: TicketQuery = {}): Promise<TicketQueryResult> {
  const local = () => sortByDepart(filterLocalTickets(query.type, query.from, query.to))

  if (USE_MOCK || query.localOnly) {
    return { list: local(), fromApi: false }
  }

  try {
    const items = await searchTickets({
      type: query.type,
      from: query.from || undefined,
      to: query.to || undefined,
      date: query.date || undefined
    })
    if (!items || items.length === 0) {
      console.warn('[ticket] 接口无候选（该线路未收录或候选表还没建），改用本地兜底')
      return { list: local(), fromApi: false }
    }
    return { list: sortByDepart(items.map(toTicketOption)), fromApi: true }
  } catch (e) {
    console.warn('[ticket] 候选接口不可用，改用本地兜底:', e)
    return { list: local(), fromApi: false }
  }
}
