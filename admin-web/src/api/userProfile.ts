import { http } from '@/utils/request'
import type { UserItem, UserProfileDetail } from '@/types'

/**
 * 管理端 AI 用户画像接口
 *
 * 路径挂在 /admin/user-profile/**，与 /admin/user/{id} 分开，避免路径匹配歧义。
 * 权限点复用 user:view / user:manage，没有新增权限点，
 * 所以不需要改角色表里的权限串（新增权限点会导致菜单不显示、页面被路由守卫重定向）。
 */

/** 人工修正画像入参：整份覆盖，允许清空；不含 userId 与回灌开关 */
export interface UserProfileEditParams {
  preferenceTags: Array<{ tag: string; weight: number }>
  freeTags: string[]
  constraintsText: string
  budgetLevel: string
  pace: string
  companions: string
  preferDays: number | null
  summaryText: string
}

/** 画像详情（无画像时返回带用户基础信息的空结构，不会 404） */
export function getUserProfile(userId: number) {
  return http.get<UserProfileDetail>(`/admin/user-profile/${userId}`)
}

/** 受控标签词表（筛选下拉与标签编辑下拉用） */
export function getProfileTags() {
  return http.get<string[]>('/admin/user-profile/tags')
}

/** 人工修正画像（整份覆盖；即时生效，用户下次生成行程时会被 AI 重写覆盖） */
export function updateUserProfile(userId: number, data: UserProfileEditParams) {
  return http.put<UserProfileDetail>(`/admin/user-profile/${userId}`, data)
}

/** 更新运营备注与标记（与画像内容分开保存） */
export function updateUserProfileRemark(userId: number, data: { remark: string; profileMark: string }) {
  return http.put<UserProfileDetail>(`/admin/user-profile/${userId}/remark`, data)
}

/** 按当前筛选条件导出（不分页，后端有上限；前端拼 CSV） */
export function exportUserList(params: Record<string, unknown>) {
  return http.get<UserItem[]>('/admin/user/export', params)
}
