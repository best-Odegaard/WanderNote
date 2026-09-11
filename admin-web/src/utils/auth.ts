/** 管理员 token / 信息本地存储 */

const TOKEN_KEY = 'admin_token'
const INFO_KEY = 'admin_info'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getInfo<T>(): T | null {
  const raw = localStorage.getItem(INFO_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export function setInfo<T>(info: T): void {
  localStorage.setItem(INFO_KEY, JSON.stringify(info))
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(INFO_KEY)
}
