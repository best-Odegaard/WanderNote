import { http } from '@/utils/request'
import type { PageResult, FeedbackItem } from '@/types'

export interface FeedbackQuery {
  pageNum: number
  pageSize: number
  status?: number
  type?: string
  keyword?: string
}

/** 意见反馈分页列表 */
export function getFeedbackList(params: FeedbackQuery) {
  return http.get<PageResult<FeedbackItem>>('/admin/feedback', params as unknown as Record<string, unknown>)
}

/** 反馈详情 */
export function getFeedbackDetail(id: number) {
  return http.get<FeedbackItem>(`/admin/feedback/${id}`)
}

/** 回复并标记已处理 */
export function handleFeedback(id: number, reply: string) {
  return http.put<void>(`/admin/feedback/${id}/handle`, { reply })
}

/** 关闭反馈 */
export function closeFeedback(id: number) {
  return http.put<void>(`/admin/feedback/${id}/close`)
}

/** 删除反馈 */
export function deleteFeedback(id: number) {
  return http.delete<void>(`/admin/feedback/${id}`)
}
