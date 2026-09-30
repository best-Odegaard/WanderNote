import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as tripApi from '@/api/trip'
import { clearActiveChatSession } from '@/utils/chatSession'
import { emptySlotState } from '@/api/trip'
import type { TripPlan, PlanResponse, SlotState } from '@/api/trip'

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

  /** 用后端返回的快照整体覆盖（后端是唯一事实来源，前端不做增量合并） */
  function setSlotState(next?: SlotState | null) {
    slotState.value = next ?? emptySlotState()
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
    clearActiveChatSession()
  }

  /** 保存行程 */
  async function saveTrip(trip?: TripPlan) {
    const data = trip || currentTrip.value
    if (!data) throw new Error('无行程数据')
    const saved = await tripApi.saveTrip(data)
    currentTrip.value = saved
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
    slotValue,
    resetForNewTrip,
    saveTrip,
    deleteTrip,
    loadHistory,
    getTripDetail
  }
})
