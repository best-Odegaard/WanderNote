import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as tripApi from '@/api/trip'
import { clearActiveChatSession } from '@/utils/chatSession'
import type { TripPlan, PlanResponse } from '@/api/trip'

export const useTripStore = defineStore('trip', () => {
  const currentTrip = ref<TripPlan | null>(null)
  const tripHistory = ref<TripPlan[]>([])
  /** AI Agent 原始返回结果，生成行程时使用 */
  const currentAiResponse = ref<PlanResponse | null>(null)
  /** 外部带入的行程上下文（如游记/景点），创建行程时作为第一轮对话提示词 */
  const pendingTripContext = ref('')

  /** 开始新的行程规划，避免沿用上一行程的临时数据和活动聊天会话 */
  function resetForNewTrip() {
    currentTrip.value = null
    currentAiResponse.value = null
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
    resetForNewTrip,
    saveTrip,
    deleteTrip,
    loadHistory,
    getTripDetail
  }
})
