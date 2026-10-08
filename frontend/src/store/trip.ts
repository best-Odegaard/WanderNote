import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as tripApi from '@/api/trip'
import { selectHotel, getTripHotel } from '@/api/hotel'
import { clearActiveChatSession } from '@/utils/chatSession'
import { emptySlotState } from '@/api/trip'
import type { TripPlan, PlanResponse, SlotState } from '@/api/trip'
import { toHotelOption, type HotelOption } from '@/utils/hotels'

export const useTripStore = defineStore('trip', () => {
  const currentTrip = ref<TripPlan | null>(null)
  const tripHistory = ref<TripPlan[]>([])
  /** AI Agent 原始返回结果，生成行程时使用 */
  const currentAiResponse = ref<PlanResponse | null>(null)
  /** 外部带入的行程上下文（如游记/景点），创建行程时作为第一轮对话提示词 */
  const pendingTripContext = ref('')

  /**
   * 槽位快照：对话过程中收集到的行程信息（目的地/天数/预算/住宿偏好…）。
   *
   * 为什么放 store 而不是 chat.vue 的局部 ref：
   * 酒店选择页、完整路线页都要读同一份数据，放页面里只能靠跨页传参。
   * 唯一事实来源仍是后端槽位引擎，这里只是它在前端的镜像。
   */
  const slotState = ref<SlotState>(emptySlotState())

  /**
   * 已经用打字回答掉的槽位（本地即时收起 chips 用）。
   *
   * 为什么需要它：后端槽位引擎要等这一轮 /travel/chat 回来才知道「用户已经答过这题」，
   * 而用户点发送之后到回复到达之间有一段时间 —— 那排选项如果还挂在屏幕上，
   * 看起来就像「我说了它没听见」，用户会以为得再点一次选项。
   *
   * 只负责这一小段空窗，不写值：真实值仍由后端从用户输入里抽
   * （见 SlotEngine.applyText），前端不猜用户的答案。
   */
  const answeredSlots = ref<string[]>([])

  function markSlotAnswered(slot: string) {
    if (!slot || answeredSlots.value.includes(slot)) return
    answeredSlots.value = [...answeredSlots.value, slot]
  }

  /** 用后端返回的快照整体覆盖（后端是唯一事实来源，前端不做增量合并） */
  function setSlotState(next?: SlotState | null) {
    slotState.value = next ?? emptySlotState()
    // 后端快照一到就以它为准：本地「已答过」的临时标记完成使命
    answeredSlots.value = []
  }

  /**
   * 住宿偏好（问卷里选的档次：经济型/舒适型/高档型/特色民宿/无要求）。
   * 只作为酒店选择页的默认筛选条件，不进后端槽位 —— 槽位里的 hotelStyle 由后端引擎管，
   * 这里是问卷这条链路自己的入参。
   */
  const hotelPreference = ref('')

  /**
   * 用户选中的酒店。
   *
   * 为什么要放 store：酒店选择页、行程详情、路线页要读同一份数据，
   * 而且路线页需要它的坐标才能把行程补成「酒店 → 各站 → 酒店」的闭环
   * （见 utils/routeBuild.ts 的 RouteHotel）。
   */
  const selectedHotel = ref<HotelOption | null>(null)

  function setHotelPreference(level: string) {
    hotelPreference.value = level || ''
  }

  /**
   * 选中一家酒店。
   * 同时把名称写回 currentTrip.hotel —— 后端 TripPlan 本来就有这个字段（无需改后端），
   * 保存行程时它能一起落库，这样「下次打开行程还记得住哪」。
   */
  function setSelectedHotel(hotel: HotelOption | null) {
    selectedHotel.value = hotel
    if (currentTrip.value) {
      currentTrip.value.hotel = hotel?.name || ''
    }
  }

  /**
   * 把当前选中的酒店落到行程上（trip_hotel + trip_plan.hotel）。
   *
   * 为什么单独一个函数：用户选酒店时行程可能还没保存（没有 id）。
   * 选了之后才生成/保存行程的话，那次选择就得在这里补写一次 ——
   * 否则行程里只有名字、没有坐标，路线页的闭环会悄悄退化成「第一站 → 最后一站」。
   *
   * 失败不抛：住宿落库是增强项，接口挂了不能把「保存行程」这件事一起带崩。
   */
  async function persistSelectedHotel(tripId: number | string) {
    const hotel = selectedHotel.value
    if (!tripId || !hotel) return
    try {
      await selectHotel({
        tripId,
        hotelCode: hotel.id,
        name: hotel.name,
        city: hotel.city,
        level: hotel.level,
        address: hotel.address,
        nearbyLandmark: hotel.nearbyLandmark,
        lng: hotel.lng,
        lat: hotel.lat,
        price: hotel.price,
        checkin: currentTrip.value?.startDate,
        checkout: currentTrip.value?.endDate
      })
    } catch (e) {
      console.warn('[trip] 住宿未能随行程落库:', e)
    }
  }

  /**
   * 读行程已保存的住宿，恢复 selectedHotel。
   *
   * 场景：用户杀进程/换设备重新打开一条旧行程 —— 内存里的选择没了，
   * 但 trip_hotel 里存着坐标，读回来后路线页才能继续画闭环。
   */
  async function loadTripHotel(tripId: number | string) {
    if (!tripId) return null
    try {
      const item = await getTripHotel(tripId)
      if (!item || !item.name) {
        selectedHotel.value = null
        return null
      }
      const option = toHotelOption(item)
      selectedHotel.value = option
      return option
    } catch (e) {
      // 旧行程本来就可能没有住宿记录；接口异常也不该挡住行程详情
      console.warn('[trip] 住宿信息读取失败:', e)
      return null
    }
  }

  /** 取某个槽位的值（没有则返回空串），模板里比链式可选取值清爽 */
  function slotValue(key: string): string {
    return slotState.value.slots?.[key]?.value ?? ''
  }

  /** 开始新的行程规划，避免沿用上一行程的临时数据和活动聊天会话 */
  function resetForNewTrip() {
    currentTrip.value = null
    currentAiResponse.value = null
    slotState.value = emptySlotState()
    hotelPreference.value = ''
    selectedHotel.value = null
    clearActiveChatSession()
  }

  /** 保存行程 */
  async function saveTrip(trip?: TripPlan) {
    const data = trip || currentTrip.value
    if (!data) throw new Error('无行程数据')
    const saved = await tripApi.saveTrip(data)
    currentTrip.value = saved
    // 行程第一次保存拿到 id 后，把之前选好的酒店补写进 trip_hotel
    if (saved.id != null) {
      await persistSelectedHotel(saved.id)
    }
    await loadHistory()
    return saved
  }

  /** 删除行程 */
  async function deleteTrip(id: number | string) {
    await tripApi.deleteTrip(id)
    tripHistory.value = tripHistory.value.filter((t) => t.id !== id)
    if (currentTrip.value?.id === id) {
      currentTrip.value = null
    }
  }

  /** 加载历史行程 */
  async function loadHistory() {
    const list = await tripApi.getMyTrips()
    tripHistory.value = list
    return list
  }

  /** 获取行程详情 */
  async function getTripDetail(id: number | string) {
    const trip = await tripApi.getTripDetail(id)
    currentTrip.value = trip
    return trip
  }

  return {
    currentTrip,
    tripHistory,
    currentAiResponse,
    pendingTripContext,
    slotState,
    setSlotState,
    answeredSlots,
    markSlotAnswered,
    slotValue,
    hotelPreference,
    selectedHotel,
    setHotelPreference,
    setSelectedHotel,
    persistSelectedHotel,
    loadTripHotel,
    resetForNewTrip,
    saveTrip,
    deleteTrip,
    loadHistory,
    getTripDetail
  }
})
