/**
 * H5 / App(WebView) 的返回守卫。
 *
 * 背景：Android 的边缘滑动返回与返回键，在 Capacitor 的 WebView 里等价于「历史回退一级」；
 * 当 WebView 历史里没有上一条时，就退化成退出整个 App。
 * uni-app 的 H5 路由靠 history.state.back 判断页内能否回上一页——直接从外部链接、
 * 或 App 冷启动恢复到内页进入时它是空的，于是「边缘滑一下」就把 App 退掉了。
 *
 * 用法（在需要防误退的页面里）：
 *   onMounted(() => { uninstall = installBackGuard() })
 *   onUnmounted(() => uninstall?.())
 *
 * 行为：
 *   - 正常从 App 内跳进来（有页内上一页）：不干预，返回手势照常回上一页；
 *   - 返回后仍停在本页（说明刚才消费掉的是兜底历史，系统再退就退出 App）：
 *     补一条兜底历史并回到首页，绝不退出应用。
 */
export function installBackGuard(): () => void {
  // #ifdef H5
  const pushGuard = () => {
    try {
      window.history.pushState({ __wnBackGuard: true }, '')
    } catch (e) {
      // 个别 WebView 在异常状态下会抛错，忽略即可
      console.warn('[backGuard] pushState 失败:', e)
    }
  }

  const currentPath = () => window.location.hash || window.location.pathname

  const state = window.history.state as { back?: string } | null
  const here = currentPath()
  if (!state || !state.back) {
    // 本页是历史里的第一条：先垫一条兜底历史，避免首次返回就退出 App
    pushGuard()
  }

  const onPopState = () => {
    if (currentPath() !== here) {
      // URL 已经变了 = 正常的页内返回，交给 uni-app 处理
      return
    }
    // 返回后还停在本页：刚才消费掉的是兜底历史，再退就要退出应用了 → 回首页
    pushGuard()
    uni.switchTab({
      url: '/pages/home/index',
      fail: () => uni.reLaunch({ url: '/pages/home/index' })
    })
  }

  window.addEventListener('popstate', onPopState)
  return () => window.removeEventListener('popstate', onPopState)
  // #endif
  // #ifndef H5
  return () => {}
  // #endif
}
