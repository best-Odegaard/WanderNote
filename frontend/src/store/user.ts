import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { storage } from '@/utils/storage'
import { TOKEN_KEY, USER_INFO_KEY } from '@/utils/constant'
import { setToken, removeToken } from '@/utils/auth'
import { clearActiveChatSession } from '@/utils/chatSession'
import * as userApi from '@/api/user'
import type { UserInfo, LoginParams, RegisterParams } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const userInfo = ref<UserInfo | null>(null)

  const isLogin = computed(() => !!token.value)

  /** 从本地存储初始化 */
  function initFromStorage() {
    token.value = storage.get<string>(TOKEN_KEY, '') || ''
    userInfo.value = storage.get<UserInfo>(USER_INFO_KEY) || null
  }

  /** 登录 */
  async function login(params: LoginParams) {
    const res = await userApi.login(params)
    token.value = res.token
    userInfo.value = res.userInfo
    setToken(res.token)
    storage.set(USER_INFO_KEY, res.userInfo)
    return res
  }

  /** 注册 */
  async function register(params: RegisterParams) {
    return userApi.register(params)
  }

  /** 退出登录 */
  function logout() {
    token.value = ''
    userInfo.value = null
    removeToken()
    storage.remove(USER_INFO_KEY)
    clearActiveChatSession()
    uni.reLaunch({ url: '/pages/auth/login' })
  }

  /** 获取用户信息 */
  async function getUserInfo() {
    const info = await userApi.getUserInfo()
    userInfo.value = info
    storage.set(USER_INFO_KEY, info)
    return info
  }

  /** 更新用户信息 */
  async function updateUserInfo(data: Partial<UserInfo>) {
    const info = await userApi.updateUserInfo(data)
    userInfo.value = { ...userInfo.value, ...info } as UserInfo
    storage.set(USER_INFO_KEY, userInfo.value)
    return info
  }

  return {
    token,
    userInfo,
    isLogin,
    initFromStorage,
    login,
    register,
    logout,
    getUserInfo,
    updateUserInfo
  }
})
