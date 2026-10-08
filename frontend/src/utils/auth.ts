import { storage } from './storage'
import { TOKEN_KEY, USER_INFO_KEY } from './constant'

/** 获取 Token */
export function getToken(): string {
  return storage.get<string>(TOKEN_KEY, '') || ''
}

/** 设置 Token */
export function setToken(token: string): void {
  storage.set(TOKEN_KEY, token)
}

/** 移除 Token */
export function removeToken(): void {
  storage.remove(TOKEN_KEY)
}

/** 是否已登录 */
export function isLoggedIn(): boolean {
  return !!getToken()
}

/**
 * 「本地登录态被清掉」的通知回调。
 *
 * 为什么需要：401 时必须在**请求层**就把令牌清掉（否则旧令牌一直躺在 storage 里，
 * `isLoggedIn()` 恒为 true，用户被困在「登录页 ↔ 内页」循环）。
 * 但请求层是最底层工具，直接 import Pinia store 会和 `store/user -> api/user -> utils/request`
 * 形成循环依赖；所以这里只发通知，由 App.vue 在启动时把 store 的重置动作注册进来。
 */
let authClearedListener: (() => void) | null = null

/** 注册登录态清理通知（App.vue 启动时调用一次） */
export function onAuthCleared(listener: () => void): void {
  authClearedListener = listener
}

/**
 * 清掉本地登录态（token + 用户信息），并通知内存里的 store 同步复位。
 *
 * 注意：只清本地、不做跳转 —— 跳转由调用方决定（401 走 redirectToLogin，
 * 主动退出走 userStore.logout 的 reLaunch）。
 */
export function clearAuthStorage(): void {
  removeToken()
  storage.remove(USER_INFO_KEY)
  try {
    authClearedListener?.()
  } catch (e) {
    console.warn('[auth] 登录态清理通知失败:', e)
  }
}

/**
 * 跳登录页的去重闩锁。
 *
 * 为什么需要：一次进页可能并发多条请求（例如探索 tab 同时发 /featured/list 与 /journal/list），
 * 每个 401 各跳一次登录页，登录页会叠好几层，用户连按几次返回都退不出去。
 * 闩锁保证「同一波 401 只跳一次」，由登录页 onShow 解除（那里重新允许下一次跳转）。
 */
let loginRedirecting = false

/** 解除跳登录闩锁（登录页 onShow 调用） */
export function resetLoginRedirectLock(): void {
  loginRedirecting = false
}

/** tabBar 页面列表（与 pages.json tabBar.list 保持一致），tab 页必须用 switchTab 跳转 */
const TAB_BAR_PAGES = [
  '/pages/home/index',
  '/pages/trip/index',
  '/pages/coming/index',
  '/pages/profile/index'
]

/** 跳转登录页（携带当前页完整路径 + query，登录成功后回跳） */
export function redirectToLogin(): void {
  if (loginRedirecting) {
    // 同一波 401 已经在跳登录页，不再叠一层
    return
  }
  loginRedirecting = true
  // 兜底：万一登录页没能 onShow（跳转失败等），5 秒后自动解锁，避免之后再也跳不了登录
  setTimeout(() => {
    loginRedirecting = false
  }, 5000)

  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1] as
    | { route: string; options?: Record<string, string> }
    | undefined
  if (!currentPage) {
    uni.navigateTo({ url: '/pages/auth/login' })
    return
  }
  // 完整路径 = route + query（保留参数，避免回跳后丢失页面状态）
  let redirect = `/${currentPage.route}`
  const options = currentPage.options as Record<string, string> | undefined
  if (options && Object.keys(options).length > 0) {
    const query = Object.entries(options)
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
      .join('&')
    if (query) redirect += `?${query}`
  }
  uni.navigateTo({
    url: `/pages/auth/login${redirect ? `?redirect=${encodeURIComponent(redirect)}` : ''}`
  })
}

/** 登录成功后的目标页跳转：tab 页用 switchTab，普通页用 redirectTo（替换登录页，返回栈不回登录页） */
export function goToRedirectPage(redirect: string): void {
  if (!redirect) return
  const path = redirect.split('?')[0]
  if (TAB_BAR_PAGES.includes(path)) {
    uni.switchTab({ url: path })
  } else {
    uni.redirectTo({ url: redirect })
  }
}
