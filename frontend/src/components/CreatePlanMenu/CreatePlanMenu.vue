<template>
  <view v-if="visible" class="menu-root" @tap="close">
    <view class="menu-mask" />
    <view class="menu-panel" @tap.stop>
      <view class="menu-item menu-item-primary" @tap="onCreateNew">
        <view class="menu-text">
          <text class="menu-title font-hand">智能规划行程</text>
          <text class="menu-desc">填写目的地、天数与偏好，进入 AI 行程助手继续补充需求</text>
        </view>
        <view class="menu-icon">
          <AppIcon name="sparkles" :size="34" color="currentColor" />
        </view>
      </view>
      <view class="menu-item" @tap="onSmartImport">
        <view class="menu-text">
          <text class="menu-title font-hand">智能导入地点/行程</text>
          <text class="menu-desc">粘贴笔记链接、行程文本，或上传图片进行识别</text>
        </view>
        <view class="menu-icon">
          <AppIcon name="link" :size="34" color="var(--text-body)" />
        </view>
      </view>
      <view class="menu-item" @tap="onCollect">
        <view class="menu-text">
          <text class="menu-title font-hand">采集识别</text>
          <text class="menu-desc">识别同时收藏你的生活</text>
        </view>
        <view class="menu-icon">
          <AppIcon name="camera" :size="34" color="var(--text-body)" />
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/store/app'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'

const appStore = useAppStore()
const { showCreateMenu: visible } = storeToRefs(appStore)

function close() {
  appStore.closeCreateMenu()
}

function requireLogin(): boolean {
  if (isLoggedIn()) return true
  close()
  uni.showToast({ title: '请先登录后创建行程', icon: 'none' })
  setTimeout(() => {
    redirectToLogin()
  }, 500)
  return false
}

function onCreateNew() {
  if (!requireLogin()) return
  close()
  uni.navigateTo({ url: '/pages/plan/wizard' })
}

function showNotDeveloped() {
  uni.showModal({
    title: '提示',
    content: '功能暂未开发，敬请期待',
    showCancel: false,
    confirmText: '知道了'
  })
}

function onSmartImport() {
  showNotDeveloped()
}

function onCollect() {
  showNotDeveloped()
}
</script>

<style lang="scss" scoped>
.menu-root {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.menu-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(8px);
}

.menu-panel {
  position: relative;
  padding: 24rpx 32rpx calc(180rpx + env(safe-area-inset-bottom));
  z-index: 1;
  animation: slideUp 0.28s ease-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(40rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--bg-card);
  border-radius: 28rpx;
  padding: 36rpx 32rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.06);
  transition: transform 0.15s ease;

  &:active {
    transform: scale(0.98);
  }
}

.menu-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--text-body);
  background: var(--bg-card);
  border: 1rpx solid var(--border);
}

.menu-item-primary {
  background: linear-gradient(135deg, #a8e6cf, #b5ead7);

  .menu-title {
    color: var(--text-body);
  }

  .menu-icon {
    color: var(--text-body);
    background: rgba(255, 255, 255, 0.55);
    border-color: rgba(27, 94, 74, 0.2);
  }
}

.menu-text {
  flex: 1;
  min-width: 0;
  padding-right: 24rpx;
}

.menu-title {
  font-size: 34rpx;
  display: block;
  color: var(--text-main);
}

.menu-desc {
  font-size: 24rpx;
  color: var(--text-tertiary);
  margin-top: 10rpx;
  display: block;
  line-height: 1.5;
}
</style>
