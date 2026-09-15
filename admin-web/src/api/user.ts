import { http } from '@/utils/request'
import type { PageResult, UserItem } from '@/types'

export interface UserQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number
  /** 画像标签筛选（受控标签名，精确匹配） */
  profileTag?: string
  /** 画像标记筛选（模糊匹配） */
  profileMark?: string
}

/** 用户分页列表 */
export function getUserList(params: UserQuery) {
  return http.get<PageResult<UserItem>>('/admin/user', params as unknown as Record<string, unknown>)
}

/** 启用/禁用用户 */
export function updateUserStatus(id: number, status: number) {
  return http.put<void>(`/admin/user/${id}/status/${status}`)
}
