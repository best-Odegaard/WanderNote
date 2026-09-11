import http from '@/utils/request'
import { USE_MOCK } from '@/utils/constant'
import * as mock from '@/api/mock/handlers'

/** 后端原始返回结构 */
interface UserLoginVO {
  id: number
  username: string
  phone?: string
  email?: string
  nickname?: string
  avatar?: string
  gender?: number
  birthday?: string
  token: string
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar: string
  phone?: string
  email?: string
  gender?: number
  birthday?: string
  bio?: string
}

export interface LoginParams {
  username: string
  password: string
}

export interface RegisterParams {
  username: string
  password: string
  nickname?: string
  phone?: string
  email?: string
}

export interface LoginResult {
  token: string
  userInfo: UserInfo
}

function voToUserInfo(vo: UserLoginVO): UserInfo {
  return {
    id: vo.id,
    username: vo.username,
    nickname: vo.nickname || vo.username,
    avatar: vo.avatar || '',
    phone: vo.phone,
    email: vo.email,
    gender: vo.gender,
    birthday: vo.birthday
  }
}

/** 用户登录 — POST /user/login */
export async function login(data: LoginParams) {
  if (USE_MOCK) return mock.mockUserLogin(data)
  const vo = await http.post<UserLoginVO>('/user/login', data, { showLoading: true, skipAuth: true })
  return {
    token: vo.token,
    userInfo: voToUserInfo(vo)
  }
}

/** 用户注册 — POST /user/register */
export function register(data: RegisterParams) {
  if (USE_MOCK) return mock.mockUserRegister(data)
  return http.post<void>('/user/register', data, { showLoading: true, skipAuth: true })
}

/** 获取用户信息 — GET /user/info */
export function getUserInfo() {
  if (USE_MOCK) return mock.mockGetUserInfo()
  return http.get<UserInfo>('/user/info')
}

/** 更新用户信息 — PUT /user/info */
export function updateUserInfo(data: Partial<UserInfo>) {
  if (USE_MOCK) return mock.mockUpdateUserInfo(data)
  return http.put<UserInfo>('/user/info', data, { showLoading: true })
}

/** 发送验证码 — POST /user/sms */
export function sendSmsCode(phone: string) {
  if (USE_MOCK) return mock.mockSendSmsCode(phone)
  return http.post<void>('/user/sms', { phone }, { skipAuth: true })
}
