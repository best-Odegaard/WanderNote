import { http } from '@/utils/request'
import type { PageResult, AdminItem, RoleItem, PermItem } from '@/types'

export interface AdminSaveParams {
  id?: number
  username: string
  password?: string
  nickname?: string
  roleId?: number
  status?: number
}

export interface RoleSaveParams {
  id?: number
  roleKey: string
  roleName: string
  perms?: string
  description?: string
}

// ── 管理员 ──

export function getAdminList(params: { pageNum: number; pageSize: number }) {
  return http.get<PageResult<AdminItem>>('/admin/manage/admin', params as unknown as Record<string, unknown>)
}

export function addAdmin(data: AdminSaveParams) {
  return http.post<void>('/admin/manage/admin', data)
}

export function updateAdmin(data: AdminSaveParams) {
  return http.put<void>('/admin/manage/admin', data)
}

export function deleteAdmin(id: number) {
  return http.delete<void>(`/admin/manage/admin/${id}`)
}

export function updateAdminStatus(id: number, status: number) {
  return http.put<void>(`/admin/manage/admin/${id}/status/${status}`)
}

// ── 角色 ──

export function getRoleList() {
  return http.get<RoleItem[]>('/admin/manage/role/list')
}

export function addRole(data: RoleSaveParams) {
  return http.post<void>('/admin/manage/role', data)
}

export function updateRole(data: RoleSaveParams) {
  return http.put<void>('/admin/manage/role', data)
}

export function deleteRole(id: number) {
  return http.delete<void>(`/admin/manage/role/${id}`)
}

// ── 权限点 ──

export function getPermList() {
  return http.get<PermItem[]>('/admin/manage/perm/list')
}
