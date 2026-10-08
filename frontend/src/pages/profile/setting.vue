<template>
  <view class="page">
    <view class="menu card">
      <!-- P2-03：消息通知原来是个假开关（ref(true)，无落盘无接口，重进又变回开启）。
           推送/通知后端未接入，留着开关等于承诺一个做不到的功能，改为如实标注「暂不可用」。 -->
      <view class="menu-item">
        <text>消息通知</text>
        <text class="value">暂不可用</text>
      </view>
      <view class="menu-item">
        <text>深色模式</text>
        <switch :checked="darkMode" @change="toggleDark" color="#3B82F6" />
      </view>
      <view class="menu-item" @tap="clearCache">
        <text>清除缓存</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item">
        <text>版本号</text>
        <text class="value">v1.0.0</text>
      </view>
    </view>

    <view class="about card">
      <text class="about-title">关于 WanderNote</text>
      <text class="about-desc">基于大语言模型的智能文旅行程规划平台，为年轻用户提供 AI 行程规划、景点推荐、活动发现与社区分享服务。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAppStore } from '@/store/app'
import { showModal, showToast } from '@/utils/feedback'
import { TOKEN_KEY, USER_INFO_KEY } from '@/utils/constant'
import { THEME_KEY } from '@/utils/theme'

const appStore = useAppStore()
const darkMode = ref(appStore.theme === 'dark')

/**
 * 清缓存时必须保留的键：登录令牌 / 用户信息 / 主题。
 * 为什么不能保留主题之外全部：`uni.clearStorageSync()` 会把 token 一起清掉 ——
 * 界面因为内存里的 store 还在仍显示已登录，但之后每个请求都 401，
 * 用户看到的是「点了清缓存，然后莫名其妙被登出」。所以一个键都不许误删。
 */
const PRESERVED_KEYS: string[] = [TOKEN_KEY, USER_INFO_KEY, THEME_KEY]

function toggleDark() {
  darkMode.value = !darkMode.value
  appStore.changeTheme(darkMode.value ? 'dark' : 'light')
}

function clearCache() {
  showModal({
    title: '清除缓存',
    content: '将清除本地缓存数据（登录状态与主题设置会保留）',
    success: (res) => {
      if (!res.confirm) return
      try {
        // 逐条删除而不是 clearStorageSync()：这样能精确放过保留键
        const keys = uni.getStorageInfoSync().keys || []
        keys
          .filter((key) => !PRESERVED_KEYS.includes(key))
          .forEach((key) => uni.removeStorageSync(key))
        showToast({ title: '已清除', icon: 'none' })
      } catch {
        showToast({ title: '清除失败，请重试', icon: 'none' })
      }
    }
  })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
  padding: 24rpx 32rpx;
}

.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28rpx 0;
  border-bottom: 1rpx solid var(--border);
  font-size: var(--fs-body);
  color: var(--text-body);

  &:last-child { border-bottom: none; }
}

.arrow, .value {
  color: var(--text-secondary);
  font-size: var(--fs-body);
}

.about {
  margin-top: 24rpx;
}

.about-title {
  font-size: var(--fs-title);
  font-weight: 600;
  display: block;
  margin-bottom: 12rpx;
}

.about-desc {
  font-size: var(--fs-body);
  color: var(--text-secondary);
  line-height: 1.8;
}
</style>
