import { http } from '@/utils/request'
import type { PageResult, JournalItem, JournalDetail } from '@/types'

export interface JournalQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number
}

/** 游记分页列表 */
export function getJournalList(params: JournalQuery) {
  return http.get<PageResult<JournalItem>>('/admin/journal', params as unknown as Record<string, unknown>)
}

/** 游记详情（含评论） */
export function getJournalDetail(id: number) {
  return http.get<JournalDetail>(`/admin/journal/${id}`)
}

/** 删除/下架游记 */
export function deleteJournal(id: number) {
  return http.delete<void>(`/admin/journal/${id}`)
}

/** 修改游记状态 */
export function updateJournalStatus(id: number, status: number) {
  return http.put<void>(`/admin/journal/${id}/status/${status}`)
}

/** 删除评论 */
export function deleteComment(commentId: number) {
  return http.delete<void>(`/admin/journal/comment/${commentId}`)
}
