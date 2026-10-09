<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import { onAuthCleared, isLoggedIn } from '@/utils/auth'

/**
 * 需要登录才能进入的页面（非 tab 页）。
 *
 * 为什么要有这份名单：项目里没有路由守卫，需登录的页面靠各自的 onMounted 判断，
 * 漏一个就变成「先渲染、再靠接口 401 兜底」——用户看到空列表/白屏后被弹去登录页。
 * 与其指望每个新页面都记得写一遍，不如在这里集中声明一次。
 *
 * 约定：
 *   · 只列**非 tab 页**。tab 页（首页/行程/探索/我的）必须能被匿名打开，
 *     否则用户一点底部 tab 就被弹走（探索 tab 之前就是这个问题，见 P0-06）；
 *     它们各自的空态负责提示「登录后可用」。
 *   · 公开内容页不要列进来：scenic/activity/community 的列表与详情都允许匿名浏览。
 */
const PROTECTED_PAGES = [
  'pages/plan/survey',
  'pages/plan/hotel',
  'pages/plan/ticket',
  'pages/plan/wizard',
  'pages/plan/import',
  'pages/ai/chat',
  'pages/trip/detail',
  'pages/trip/edit',
  'pages/trip/route',
  'pages/profile/edit',
  'pages/profile/setting',
  'pages/profile/journals',
  'pages/community/publish'
]

/** 目标路径是否属于「必须登录」的页面 */
function isProtected(url: string): boolean {
  const path = url.split('?')[0].replace(/^\//, '')
  return PROTECTED_PAGES.some((p) => path === p || path.startsWith(`${p}/`))
}

/**
 * 统一守卫：未登录访问受保护页面 → 改道登录页，并带上原目标（登录后自动回跳）。
 *
 * 为什么改道而不是「拦下来弹个 toast」：
 *   不跳走的话用户停在空白页上没有出路；改道登录页 + redirect 是唯一能自愈的做法
 *   （redirectToLogin 用的是同一套约定）。
 * 只挂 navigateTo / redirectTo / reLaunch，**不拦 switchTab**：
 *   受保护的都是非 tab 页，而登录页也不是 tab 页，
 *   拦 switchTab 只会把「切 tab」变成一次必然失败的跳转。
 */
function installRouteGuard() {
  const guard = (url?: string): string | undefined => {
    if (!url || isLoggedIn() || !isProtected(url)) return url
    return `/pages/auth/login?redirect=${encodeURIComponent(url)}`
  }
  ;(['navigateTo', 'redirectTo', 'reLaunch'] as const).forEach((api) => {
    uni.addInterceptor(api, {
      invoke(args: { url?: string }) {
        const next = guard(args.url)
        if (next && next !== args.url) {
          args.url = next
        }
        return args
      }
    })
  })
}

onLaunch(() => {
  // 恢复深色/浅色主题
  useAppStore().restoreTheme()
  // 恢复登录态
  const userStore = useUserStore()
  userStore.initFromStorage()

  // 请求层发现 401 时会清 storage（避免旧令牌导致登录态判断失真、登录页反复弹），
  // 但请求层不能直接引 Pinia store（会形成 store -> api -> request 的循环依赖），
  // 所以由这里把「内存态复位」注册进去，保证 storage 与页面状态同步。
  onAuthCleared(() => userStore.clearLocal())

  // 统一登录守卫（原先只有 plan/wizard、plan/import、ai/chat 自己在 onMounted 里判断）
  installRouteGuard()
})
</script>

<style lang="scss">
@import '@/styles/common.scss';
</style>
