import { ref } from 'vue'
import { useUserStore } from '@/store/user'
import { isLoggedIn, goToRedirectPage, redirectToLogin } from '@/utils/auth'
import { showToast } from '@/utils/feedback'

/** 登录相关 Hook */
export function useLogin() {
  const userStore = useUserStore()
  const loading = ref(false)
  /**
   * 是否已经提交过登录（成功之前一直为 true）。
   *
   * 为什么单独一个标志：`loading` 在 finally 里立刻复位，而跳转在 1 秒后才发生 ——
   * 这 1 秒内按钮已经可点，慢网/手抖会再发一次 /user/login，
   * 两个 setTimeout 叠加还会出现两次导航（redirectTo 与 navigateBack 打架）。
   */
  const submitting = ref(false)

  /** 登录成功后回跳目标页；redirect 为空时回退到"返回上一页/首页" */
  async function handleLogin(username: string, password: string, redirect?: string) {
    if (submitting.value) return
    submitting.value = true
    loading.value = true
    try {
      await userStore.login({ username, password })
      showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        // 优先回跳到触发登录前的页面（redirect 参数由 login 页 onLoad 解析）
        if (redirect) {
          goToRedirectPage(redirect)
          return
        }
        const pages = getCurrentPages()
        if (pages.length > 1) {
          uni.navigateBack()
        } else {
          uni.switchTab({ url: '/pages/home/index' })
        }
      }, 1000)
    } catch (e) {
      // 登录失败要允许重试：只有成功才把 submitting 留到跳转完成
      submitting.value = false
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 需要登录才能继续的动作：未登录就先跳登录页。
   *
   * 为什么改成 redirectToLogin()：
   *   原来弹一个「请先登录」modal，点「去登录」只 `navigateTo('/pages/auth/login')` ——
   *   不带 redirect，登录成功后只能回上一页，用户想做的事（发布游记、创建行程、收藏）
   *   得手动重来一遍。redirectToLogin() 会把当前页完整路径（含 query）带上，
   *   登录后自动回跳，意图不丢。项目里现在只保留这一种登录引导方式。
   */
  function checkLogin(): boolean {
    if (!isLoggedIn()) {
      showToast({ title: '请先登录', icon: 'none' })
      setTimeout(() => redirectToLogin(), 300)
      return false
    }
    return true
  }

  return { loading, submitting, handleLogin, checkLogin, isLogin: userStore.isLogin }
}
