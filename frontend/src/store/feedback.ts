import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastIcon = 'success' | 'error' | 'loading' | 'none'

export interface FeedbackToast {
  id: number
  title: string
  icon: ToastIcon
  duration: number
}

export interface FeedbackModalState {
  id: number
  title: string
  content: string
  showCancel: boolean
  cancelText: string
  cancelColor: string
  confirmText: string
  confirmColor: string
  editable: boolean
  placeholderText: string
  /** 确认色为危险色（删除类操作）时启用红色强调样式 */
  danger: boolean
}

export interface FeedbackSheetItem {
  label: string
  icon: string
}

export interface FeedbackSheetState {
  id: number
  items: FeedbackSheetItem[]
  itemColor: string
}

let seq = 0
export function nextFeedbackId(): number {
  seq += 1
  return seq
}

/**
 * 全局交互反馈的 UI 状态。
 * 只存弹层展示所需状态，不承载任何业务数据；调用方与回调见 utils/feedback.ts。
 */
export const useFeedbackStore = defineStore('feedback', () => {
  const toast = ref<FeedbackToast | null>(null)
  const modal = ref<FeedbackModalState | null>(null)
  const sheet = ref<FeedbackSheetState | null>(null)
  const loadingCount = ref(0)
  const loadingTitle = ref('加载中...')

  function setToast(value: FeedbackToast | null) {
    toast.value = value
  }

  function setModal(value: FeedbackModalState | null) {
    modal.value = value
  }

  function setSheet(value: FeedbackSheetState | null) {
    sheet.value = value
  }

  function enterLoading(title: string) {
    loadingCount.value += 1
    loadingTitle.value = title
  }

  function leaveLoading() {
    loadingCount.value = Math.max(0, loadingCount.value - 1)
  }

  return {
    toast,
    modal,
    sheet,
    loadingCount,
    loadingTitle,
    setToast,
    setModal,
    setSheet,
    enterLoading,
    leaveLoading
  }
})
