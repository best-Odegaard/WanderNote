/**
 * 主题应用工具：将主题写入根元素（驱动 CSS 变量）并同步原生导航栏
 */
import type { ThemeMode } from '@/store/app'

/** 主题存储键名 */
export const THEME_KEY = 'theme'

/** 是否存在 DOM 根元素（H5 / App 环境可切换根节点属性） */
const hasRootElement = () => typeof document !== 'undefined' && !!document.documentElement

/** 读取本地存储的主题 */
export function getStoredTheme(): ThemeMode {
  try {
    const stored = uni.getStorageSync(THEME_KEY)
    return stored === 'dark' ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

/** 应用主题：根元素 data-theme + 原生导航栏颜色 + 持久化 */
export function applyTheme(mode: ThemeMode) {
  // 1. 根元素 data-theme，驱动 theme.scss 中的 CSS 变量切换
  if (hasRootElement()) {
    document.documentElement.setAttribute('data-theme', mode)
  }
  // 2. 同步原生导航栏（非自定义导航栏的页面）
  uni.setNavigationBarColor({
    frontColor: mode === 'dark' ? '#ffffff' : '#000000',
    backgroundColor: mode === 'dark' ? '#0F172A' : '#FFFFFF',
    // H5 自定义导航栏页面无原生导航栏可设置，fail 时静默忽略，避免未处理 rejection 报错
    fail: () => {}
  })
  // 3. 持久化选择
  uni.setStorageSync(THEME_KEY, mode)
}

/** 初始化：从本地存储恢复主题并应用 */
export function initTheme(): ThemeMode {
  const mode = getStoredTheme()
  applyTheme(mode)
  return mode
}
