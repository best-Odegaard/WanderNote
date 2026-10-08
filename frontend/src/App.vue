<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import { onAuthCleared } from '@/utils/auth'

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
})
</script>

<style lang="scss">
@import '@/styles/common.scss';
</style>
