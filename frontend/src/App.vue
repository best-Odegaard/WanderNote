<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'

onLaunch(() => {
  // 恢复深色/浅色主题
  useAppStore().restoreTheme()
  // 恢复登录态
  useUserStore().initFromStorage()
})
</script>

<style lang="scss">
@import '@/styles/common.scss';
</style>
