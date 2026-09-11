import { defineStore } from 'pinia'
import { http } from '@/utils/request'
import { getToken, setToken, getInfo, setInfo, clearAuth } from '@/utils/auth'
import type { AdminInfo, LoginResult } from '@/types'

interface LoginParams {
  username: string
  password: string
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    info: getInfo<AdminInfo>()
  }),
  getters: {
    isLogin: (state) => !!state.token,
    perms: (state) => state.info?.perms || []
  },
  actions: {
    async login(params: LoginParams) {
      const res = await http.post<LoginResult>('/admin/login', params)
      this.token = res.token
      this.info = {
        id: res.id,
        username: res.username,
        nickname: res.nickname,
        roleId: res.roleId,
        roleKey: res.roleKey,
        roleName: res.roleName,
        perms: res.perms || []
      }
      setToken(res.token)
      setInfo(this.info)
    },
    /** 刷新当前管理员信息（登录后/页面刷新时调用） */
    async fetchInfo() {
      const res = await http.get<AdminInfo>('/admin/info')
      this.info = res
      setInfo(res)
    },
    logout() {
      this.token = ''
      this.info = null
      clearAuth()
    },
    hasPerm(perm?: string) {
      if (!perm) return true
      return (this.info?.perms || []).includes(perm)
    }
  }
})
