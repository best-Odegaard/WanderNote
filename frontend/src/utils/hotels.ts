/**
 * 酒店（住宿）候选数据。
 *
 * 数据来源分两层（这是刻意的，不是临时方案）：
 *   1) 首选后端 `GET /hotel/search`（hotel_candidate 表，运营可维护、可加城市不发版）；
 *   2) 接口不可用/未收录该城市时，用手写的本地候选表兜底。
 *      酒店是行程闭环的一环，接口挂了不能连「晚上住哪」都选不了。
 *
 * 两层用**同一套 hotel_code**（如 zq-sf-1）与同一套坐标：
 *   · 断网时用户选的酒店，联网后仍能按 code 对上号（trip_hotel.hotel_code）；
 *   · 兜底候选（按城市临时生成的那几家）没有 code、坐标是 0，
 *     路线页据此不闭环（见 pages/trip/route.vue 的 resolveHotel），
 *     退回「第一站 → 最后一站」，不会画出一条通向 (0,0) 的假路线。
 *
 * 坐标是必需的：路线页要靠它把当天行程补成「酒店出发 → 各站 → 返回酒店」的闭环
 * （见 utils/routeBuild.ts 的 RouteHotel）。有坐标就不必再走地理编码。
 *
 * 价格一律是**参考价**，展示必须带「以携程为准」——本站没有库存/房态，也不做站内下单。
 */
import { searchHotels, type HotelItem } from '@/api/hotel'
import { USE_MOCK } from './constant'

/** 住宿档次，取值与问卷「住宿偏好」的选项一一对应 */
export type HotelLevel = '经济型' | '舒适型' | '高档型' | '特色民宿'

/** 问卷住宿偏好里「无要求」的取值：不参与档次过滤 */
export const HOTEL_LEVELS: HotelLevel[] = ['经济型', '舒适型', '高档型', '特色民宿']

/** 不限档次（问卷里选「无要求」或用户手动切换） */
export const HOTEL_LEVEL_ANY = '不限'

export interface HotelOption {
  id: string
  name: string
  city: string
  level: HotelLevel
  /** 评分 0~5 */
  rating: number
  /** 参考价（元/晚），以携程为准（非实时价） */
  price: number
  /** 详细地址，直接用于导航与地理编码兜底 */
  address: string
  /** 附近地标：携程深链关键词之一（酒店名 + 地标），也是位置筛选的匹配源 */
  nearbyLandmark?: string
  /** 离市中心/主要景区的直线距离（公里），列表里给一个直观的量级 */
  distanceKm: number
  /** 卖点标签 */
  tags: string[]
  cover: string
  /** 经纬度：路线闭环用，避免依赖地图 Key 去地理编码 */
  lat: number
  lng: number
  /** 一句话简介 */
  desc: string
  /**
   * 携程深链：后端返回的优先；本地兜底候选没有，由 utils/deeplink.ts 现场拼一条。
   * 只有「城市与酒店名都为空」时才会真的拿不到链接，那时页面隐藏「携程预订」。
   */
  ctripUrl?: string
}

const cover = (seed: string) => `https://picsum.photos/seed/${seed}/480/320`

/**
 * 候选酒店表。
 *
 * 城市覆盖问卷里出现过的几个（肇庆/广州/桂林，以及对话里常见的成都/重庆/杭州），
 * 其余目的地由 {@link getHotelOptions} 用通用候选兜底，保证任何城市都选得出「晚上住哪」。
 */
const HOTELS: HotelOption[] = [
  // ── 肇庆 ──
  {
    id: 'zq-jj-1', name: '星湖假日酒店', city: '肇庆', level: '经济型', rating: 4.5, price: 218,
    address: '肇庆市端州区星湖大道 12 号', distanceKm: 1.2, tags: ['近星湖', '免费停车'],
    nearbyLandmark: '七星岩',
    cover: cover('zhaoqing-hotel-1'), lat: 23.0621, lng: 112.4742,
    desc: '步行 10 分钟到七星岩牌坊，性价比高，适合一天两晚的轻装出行。'
  },
  {
    id: 'zq-sf-1', name: '星湖景畔酒店', city: '肇庆', level: '舒适型', rating: 4.7, price: 368,
    address: '肇庆市端州区星湖西路 8 号', distanceKm: 0.8, tags: ['湖景房', '含双早'],
    nearbyLandmark: '星湖',
    cover: cover('zhaoqing-hotel-2'), lat: 23.0588, lng: 112.4688,
    desc: '正对星湖，房间能看到湖面晨雾，出门就是环湖绿道。'
  },
  {
    id: 'zq-gd-1', name: '七星岩温德姆酒店', city: '肇庆', level: '高档型', rating: 4.8, price: 688,
    address: '肇庆市端州区七星岩景区南门旁', distanceKm: 0.3, tags: ['景区门口', '泳池'],
    nearbyLandmark: '七星岩',
    cover: cover('zhaoqing-hotel-3'), lat: 23.0549, lng: 112.4801,
    desc: '就在景区南门口，早上不用赶路，适合带老人小孩的家庭。'
  },
  {
    id: 'zq-ms-1', name: '岩前村文创民宿', city: '肇庆', level: '特色民宿', rating: 4.6, price: 328,
    address: '肇庆市高要区岩前村 3 巷', distanceKm: 2.4, tags: ['文艺', '窑烤面包'],
    nearbyLandmark: '岩前村',
    cover: cover('zhaoqing-hotel-4'), lat: 23.0451, lng: 112.4895,
    desc: '由老宅改造，院子里有咖啡馆，晚上安静得只听到虫鸣。'
  },

  // ── 广州 ──
  {
    id: 'gz-jj-1', name: '广州塔旁如家精选', city: '广州', level: '经济型', rating: 4.4, price: 258,
    address: '广州市海珠区阅江中路 88 号', distanceKm: 1.1, tags: ['近地铁', '近广州塔'],
    nearbyLandmark: '广州塔',
    cover: cover('guangzhou-hotel-1'), lat: 23.1065, lng: 113.3215,
    desc: '出门 5 分钟到地铁站，去珠江新城和上下九都方便。'
  },
  {
    id: 'gz-sf-1', name: '珠江新城希尔顿花园', city: '广州', level: '舒适型', rating: 4.7, price: 528,
    address: '广州市天河区华夏路 22 号', distanceKm: 0.6, tags: ['CBD', '健身房'],
    nearbyLandmark: '花城广场',
    cover: cover('guangzhou-hotel-2'), lat: 23.1219, lng: 113.3232,
    desc: '城市中心位置，晚上散步就能看小蛮腰灯光秀。'
  },
  {
    id: 'gz-gd-1', name: '白天鹅宾馆', city: '广州', level: '高档型', rating: 4.9, price: 1088,
    address: '广州市荔湾区沙面南街 1 号', distanceKm: 3.2, tags: ['江景', '老牌五星'],
    nearbyLandmark: '沙面',
    cover: cover('guangzhou-hotel-3'), lat: 23.1043, lng: 113.2401,
    desc: '沙面岛上的老牌江景酒店，早茶和中庭园林都值得专程体验。'
  },
  {
    id: 'gz-ms-1', name: '永庆坊骑楼民宿', city: '广州', level: '特色民宿', rating: 4.6, price: 398,
    address: '广州市荔湾区恩宁路 99 号', distanceKm: 2.8, tags: ['骑楼', '老西关'],
    nearbyLandmark: '永庆坊',
    cover: cover('guangzhou-hotel-4'), lat: 23.1139, lng: 113.2379,
    desc: '住在西关骑楼里，楼下就是肠粉与糖水铺。'
  },

  // ── 桂林 ──
  {
    id: 'gl-jj-1', name: '阳朔西街青旅', city: '桂林', level: '经济型', rating: 4.5, price: 158,
    address: '桂林市阳朔县西街 66 号', distanceKm: 0.4, tags: ['西街口', '可拼车'],
    nearbyLandmark: '阳朔西街',
    cover: cover('guilin-hotel-1'), lat: 24.7781, lng: 110.4966,
    desc: '就在西街入口，晚上热闹，白天可以约人拼车去遇龙河。'
  },
  {
    id: 'gl-sf-1', name: '漓江畔观景酒店', city: '桂林', level: '舒适型', rating: 4.7, price: 418,
    address: '桂林市象山区滨江路 15 号', distanceKm: 0.9, tags: ['江景房', '象鼻山'],
    nearbyLandmark: '象鼻山',
    cover: cover('guilin-hotel-2'), lat: 25.2631, lng: 110.2987,
    desc: '阳台正对漓江与象鼻山，日出时分最好看。'
  },
  {
    id: 'gl-gd-1', name: '阳朔悦榕庄', city: '桂林', level: '高档型', rating: 4.9, price: 1680,
    address: '桂林市阳朔县遇龙河畔', distanceKm: 6.5, tags: ['山景泳池', '度假'],
    nearbyLandmark: '遇龙河',
    cover: cover('guilin-hotel-3'), lat: 24.8103, lng: 110.4388,
    desc: '喀斯特峰林环抱的度假村，适合把行程排得慢一点。'
  },
  {
    id: 'gl-ms-1', name: '遇龙河竹院民宿', city: '桂林', level: '特色民宿', rating: 4.8, price: 468,
    address: '桂林市阳朔县旧县村 42 号', distanceKm: 4.2, tags: ['田园', '骑行'],
    nearbyLandmark: '遇龙河',
    cover: cover('guilin-hotel-4'), lat: 24.8031, lng: 110.4522,
    desc: '院里能看到稻田与峰林，免费借自行车沿河骑行。'
  },

  // ── 成都 ──
  {
    id: 'cd-jj-1', name: '春熙路7天优品', city: '成都', level: '经济型', rating: 4.3, price: 198,
    address: '成都市锦江区红星路三段 8 号', distanceKm: 0.5, tags: ['近地铁', '夜市'],
    nearbyLandmark: '春熙路',
    cover: cover('chengdu-hotel-1'), lat: 30.6572, lng: 104.0815,
    desc: '走两步就是春熙路与太古里，吃宵夜不用打车。'
  },
  {
    id: 'cd-sf-1', name: '宽窄巷子亚朵酒店', city: '成都', level: '舒适型', rating: 4.7, price: 428,
    address: '成都市青羊区长顺上街 21 号', distanceKm: 0.6, tags: ['近宽窄巷子', '书店'],
    nearbyLandmark: '宽窄巷子',
    cover: cover('chengdu-hotel-2'), lat: 30.6699, lng: 104.0553,
    desc: '巷子口的位置，早起可以直接进宽窄巷子拍空镜。'
  },
  {
    id: 'cd-gd-1', name: '成都博舍', city: '成都', level: '高档型', rating: 4.9, price: 1288,
    address: '成都市锦江区笔帖式街 81 号', distanceKm: 1.0, tags: ['设计酒店', '太古里'],
    nearbyLandmark: '太古里',
    cover: cover('chengdu-hotel-3'), lat: 30.6543, lng: 104.0836,
    desc: '藏在太古里里的清代院落改造酒店，安静与服务都在线。'
  },
  {
    id: 'cd-ms-1', name: '锦里川西小院', city: '成都', level: '特色民宿', rating: 4.6, price: 358,
    address: '成都市武侯区武侯祠大街 231 号', distanceKm: 2.2, tags: ['川西民居', '盖碗茶'],
    nearbyLandmark: '锦里',
    cover: cover('chengdu-hotel-4'), lat: 30.6459, lng: 104.0427,
    desc: '院子里的盖碗茶免费喝，晚上能听到川剧票友吊嗓子。'
  },

  // ── 重庆 ──
  {
    id: 'cq-jj-1', name: '解放碑城市便捷', city: '重庆', level: '经济型', rating: 4.4, price: 188,
    address: '重庆市渝中区民权路 12 号', distanceKm: 0.3, tags: ['解放碑', '轻轨站'],
    nearbyLandmark: '解放碑',
    cover: cover('chongqing-hotel-1'), lat: 29.5573, lng: 106.5771,
    desc: '出门就是解放碑步行街，去洪崖洞步行 15 分钟。'
  },
  {
    id: 'cq-sf-1', name: '洪崖洞江景亚朵', city: '重庆', level: '舒适型', rating: 4.7, price: 458,
    address: '重庆市渝中区嘉滨路 88 号', distanceKm: 0.2, tags: ['江景', '夜景'],
    nearbyLandmark: '洪崖洞',
    cover: cover('chongqing-hotel-2'), lat: 29.5648, lng: 106.5817,
    desc: '房间正对千厮门大桥，晚上不用挤人群也能看夜景。'
  },
  {
    id: 'cq-gd-1', name: '重庆尼依格罗酒店', city: '重庆', level: '高档型', rating: 4.8, price: 1188,
    address: '重庆市江北区北滨一路 1 号', distanceKm: 2.6, tags: ['高楼层', '两江夜景'],
    nearbyLandmark: '江北嘴',
    cover: cover('chongqing-hotel-3'), lat: 29.5718, lng: 106.5665,
    desc: '60 层以上的落地窗，两江交汇夜景一览无遗。'
  },
  {
    id: 'cq-ms-1', name: '山城步道吊脚楼民宿', city: '重庆', level: '特色民宿', rating: 4.5, price: 338,
    address: '重庆市渝中区中兴路 155 号', distanceKm: 1.4, tags: ['吊脚楼', '步道'],
    nearbyLandmark: '山城步道',
    cover: cover('chongqing-hotel-4'), lat: 29.5527, lng: 106.5691,
    desc: '建在崖壁上的吊脚楼，推窗就是长江与轻轨穿楼。'
  },

  // ── 杭州 ──
  {
    id: 'hz-jj-1', name: '西湖湖滨如家', city: '杭州', level: '经济型', rating: 4.4, price: 238,
    address: '杭州市上城区平海路 55 号', distanceKm: 0.7, tags: ['近西湖', '近地铁'],
    nearbyLandmark: '西湖',
    cover: cover('hangzhou-hotel-1'), lat: 30.2532, lng: 120.1678,
    desc: '走到湖滨三公园 8 分钟，晚上看音乐喷泉方便。'
  },
  {
    id: 'hz-sf-1', name: '西子湖四季酒店', city: '杭州', level: '高档型', rating: 4.9, price: 1588,
    address: '杭州市西湖区灵隐路 5 号', distanceKm: 2.1, tags: ['园林', '临湖'],
    nearbyLandmark: '杨公堤',
    cover: cover('hangzhou-hotel-2'), lat: 30.2489, lng: 120.1311,
    desc: '园林式布局，从酒店后门可以直接走到杨公堤。'
  },
  {
    id: 'hz-ms-1', name: '龙井茶园民宿', city: '杭州', level: '特色民宿', rating: 4.7, price: 528,
    address: '杭州市西湖区龙井村 168 号', distanceKm: 5.4, tags: ['茶山', '安静'],
    nearbyLandmark: '龙井村',
    cover: cover('hangzhou-hotel-3'), lat: 30.2221, lng: 120.1147,
    desc: '住在茶山里，早上在露台喝茶看云雾散去。'
  },
  {
    id: 'hz-jj-2', name: '河坊街青年旅舍', city: '杭州', level: '经济型', rating: 4.5, price: 168,
    address: '杭州市上城区河坊街 118 号', distanceKm: 1.5, tags: ['老街', '可拼车'],
    nearbyLandmark: '河坊街',
    cover: cover('hangzhou-hotel-4'), lat: 30.2402, lng: 120.1686,
    desc: '老街上，小吃与南宋御街都在步行圈内。'
  }
]

/** 目的地命不中候选表时的通用兜底：任何城市都能选出「一晚住哪」 */
const FALLBACK_HOTELS = (city: string): HotelOption[] => [
  {
    id: `${city}-jj-1`, name: `${city}市中心便捷酒店`, city, level: '经济型', rating: 4.4, price: 208,
    address: `${city}市中心商圈附近`, distanceKm: 0.5, tags: ['近商圈', '交通方便'],
    cover: cover(`${city}-hotel-1`), lat: 0, lng: 0,
    desc: '标准连锁房型，位置在商圈里，吃饭与出行都省事。'
  },
  {
    id: `${city}-sf-1`, name: `${city}城市精选酒店`, city, level: '舒适型', rating: 4.6, price: 398,
    address: `${city}中心城区主干道旁`, distanceKm: 0.8, tags: ['含双早', '安静'],
    cover: cover(`${city}-hotel-2`), lat: 0, lng: 0,
    desc: '房型宽敞、隔音好，适合一天走很多路之后好好睡一觉。'
  },
  {
    id: `${city}-gd-1`, name: `${city}地标景观酒店`, city, level: '高档型', rating: 4.8, price: 888,
    address: `${city}地标景点旁`, distanceKm: 0.3, tags: ['景观房', '行政酒廊'],
    cover: cover(`${city}-hotel-3`), lat: 0, lng: 0,
    desc: '高层景观房，白天看城景、晚上看灯光，位置就在核心景区边。'
  },
  {
    id: `${city}-ms-1`, name: `${city}老城特色民宿`, city, level: '特色民宿', rating: 4.7, price: 368,
    address: `${city}老城区历史街区`, distanceKm: 1.6, tags: ['在地体验', '小院'],
    cover: cover(`${city}-hotel-4`), lat: 0, lng: 0,
    desc: '由老房子改造，院子不大但很有当地生活气。'
  }
]

/**
 * 取某城市的候选酒店。
 *
 * @param city 目的地城市（空值 = 不限制，返回全部）
 * @param level 住宿档次；传 {@link HOTEL_LEVEL_ANY} 或留空表示不限
 */
export function getHotelOptions(city?: string, level?: string): HotelOption[] {
  const cityName = (city || '').trim()
  const inCity = cityName
    ? HOTELS.filter((h) => h.city === cityName || cityName.includes(h.city))
    : [...HOTELS]

  const list = inCity.length > 0 ? inCity : FALLBACK_HOTELS(cityName || '目的地')

  if (!level || level === HOTEL_LEVEL_ANY) return list
  const filtered = list.filter((h) => h.level === level)
  // 该城市在这个档次下没有候选时，宁可放宽到全部，也不要给一个空列表
  return filtered.length > 0 ? filtered : list
}

/** 价格展示：`¥368/晚` */
export function formatHotelPrice(price: number): string {
  return `¥${price}/晚`
}

/**
 * 本地候选表的完整筛选（城市 + 档次 + 位置关键词）。
 *
 * 与后端的筛选规则保持一致，包括那两条「宁可放宽也不返回空」：
 * 档次命中 0 家 → 放宽到全部；位置关键词命中 0 家 → 放宽到全城。
 * 这样联网/断网两种情况下，用户看到的列表行为是一样的。
 */
export function filterLocalHotels(city?: string, level?: string, area?: string): HotelOption[] {
  const base = getHotelOptions(city, level)
  const keyword = (area || '').trim()
  if (!keyword) return base

  const hit = base.filter(
    (h) =>
      (h.nearbyLandmark || '').includes(keyword) ||
      h.address.includes(keyword) ||
      h.name.includes(keyword)
  )
  return hit.length > 0 ? hit : base
}

/** 后端 HotelVO → 页面用的 HotelOption（补默认值，避免可选字段到处判空） */
export function toHotelOption(item: HotelItem): HotelOption {
  const level = HOTEL_LEVELS.includes(item.level as HotelLevel)
    ? (item.level as HotelLevel)
    : HOTEL_LEVELS[0]
  return {
    id: item.id || `${item.city || ''}-${item.name}`,
    name: item.name,
    city: item.city || '',
    level,
    rating: item.rating ?? 0,
    price: item.price ?? 0,
    address: item.address || '',
    nearbyLandmark: item.nearbyLandmark,
    distanceKm: item.distanceKm ?? 0,
    tags: item.tags || [],
    cover: item.cover || cover(`hotel-${item.id || item.name}`),
    lat: item.lat ?? 0,
    lng: item.lng ?? 0,
    desc: item.desc || '',
    ctripUrl: item.ctripUrl
  }
}

/** 与后端一致的档次取值判断 */
function isLevelAny(level?: string): boolean {
  const v = (level || '').trim()
  return !v || v === HOTEL_LEVEL_ANY || v === '无要求'
}

export interface HotelQuery {
  city?: string
  /** 档次：经济型/舒适型/高档型/特色民宿；不限/无要求/空 = 不过滤 */
  level?: string
  /** 位置关键词（地标/地址）；命不中会自动放宽 */
  area?: string
  /** YYYY-MM-DD，仅用于拼携程深链 */
  checkin?: string
  checkout?: string
  /** 强制只读本地兜底（接口明确不可用时） */
  localOnly?: boolean
}

export interface HotelQueryResult {
  list: HotelOption[]
  /** 数据来自接口（true）还是本地兜底（false）——页面据此决定要不要提示「离线候选」 */
  fromApi: boolean
}

/**
 * 取候选酒店：优先接口，失败/为空则本地兜底。
 *
 * 为什么「接口返回空」也要兜底：
 *   hotel_candidate 是运营维护的表（V2.7 建表 + 种子数据）。
 *   若该 DDL 还没在某套环境执行，接口会 500、表为空 —— 这时不能把「选酒店」整步卡死，
 *   本地候选表仍然能把「选酒店 → 行程闭环」跑通，等表建好自动切到接口数据。
 */
export async function fetchHotelOptions(query: HotelQuery = {}): Promise<HotelQueryResult> {
  const local = () => filterLocalHotels(query.city, query.level, query.area)

  if (USE_MOCK || query.localOnly) {
    return { list: local(), fromApi: false }
  }

  try {
    const items = await searchHotels({
      city: query.city || undefined,
      checkin: query.checkin || undefined,
      checkout: query.checkout || undefined,
      // 档次「不限」不传，让后端别做无意义的过滤
      style: isLevelAny(query.level) ? undefined : query.level,
      area: query.area || undefined
    })
    if (!items || items.length === 0) {
      console.warn('[hotel] 接口无候选（候选表可能还没建/该城市未收录），改用本地兜底')
      return { list: local(), fromApi: false }
    }
    return { list: items.map(toHotelOption), fromApi: true }
  } catch (e) {
    console.warn('[hotel] 候选接口不可用，改用本地兜底:', e)
    return { list: local(), fromApi: false }
  }
}
