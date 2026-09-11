import { http } from '@/utils/request'
import type { LoginResult, AdminInfo } from '@/types'

export interface LoginParams {
  username: string
  password: string
}

/** 管理员登录 */
export function login(params: LoginParams) {
  return http.post<LoginResult>('/admin/login', params)
}

/** 获取当前管理员信息 */
export function getAdminInfo() {
  return http.get<AdminInfo>('/admin/info')
}

/** 登出 */
export function logout() {
  return http.post<void>('/admin/logout')
}
