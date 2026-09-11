import { ref } from 'vue'
import { useUserStore } from '@/store/user'
import { isLoggedIn, goToRedirectPage } from '@/utils/auth'

/** 登录相关 Hook */
export function useLogin() {
  const userStore = useUserStore()
  const loading = ref(false)

  /** 登录成功后回跳目标页；redirect 为空时回退到"返回上一页/首页" */
  async function handleLogin(username: string, password: string, redirect?: string) {
    loading.value = true
    try {
      await userStore.login({ username, password })
      uni.showToast({ title: '登录成功', icon: 'success' })
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
    } finally {
      loading.value = false
    }
  }

  function checkLogin(): boolean {
    if (!isLoggedIn()) {
      uni.showModal({
        title: '提示',
        content: '请先登录',
        confirmText: '去登录',
        success: (res) => {
          if (res.confirm) {
            uni.navigateTo({ url: '/pages/auth/login' })
          }
        }
      })
      return false
    }
    return true
  }

  return { loading, handleLogin, checkLogin, isLogin: userStore.isLogin }
}
