import http from '@/utils/request'
import { USE_MOCK } from '@/utils/constant'
import * as mock from '@/api/mock/handlers'

/** 提交意见反馈参数（对齐后端 UserFeedbackSubmitDTO） */
export interface SubmitFeedbackParams {
  type: string
  content: string
  /** 图片URL列表（COS 上传完成后得到） */
  images?: string[]
  contact?: string
}

/** 提交意见反馈 — POST /feedback/submit（需登录） */
export function submitFeedback(data: SubmitFeedbackParams) {
  if (USE_MOCK) return mock.mockSubmitFeedback(data)
  return http.post<void>('/feedback/submit', data, { showLoading: true, loadingText: '提交中...' })
}
