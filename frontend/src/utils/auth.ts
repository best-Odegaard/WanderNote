import { storage } from './storage'
import { TOKEN_KEY } from './constant'

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

/** tabBar 页面列表（与 pages.json tabBar.list 保持一致），tab 页必须用 switchTab 跳转 */
const TAB_BAR_PAGES = [
  '/pages/home/index',
  '/pages/trip/index',
  '/pages/coming/index',
  '/pages/profile/index'
]

/** 跳转登录页（携带当前页完整路径 + query，登录成功后回跳） */
export function redirectToLogin(): void {
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
