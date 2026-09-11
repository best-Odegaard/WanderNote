<template>
  <view class="page page-with-tabbar">
    <view class="coming-wrap">
      <view class="coming-icon soft-shadow">
        <AppIcon name="compass" :size="96" color="var(--mint-primary, #86dfc4)" :stroke-width="1.8" />
      </view>
      <text class="coming-title">探索功能暂未开发</text>
      <text class="coming-desc">该板块正在建设中，敬请期待</text>
      <view class="coming-btn" @tap="goHome">
        <AppIcon name="home" :size="30" color="#fff" />
        <text>返回首页</text>
      </view>
    </view>

    <!-- #ifndef MP-WEIXIN -->
    <AppTabBar />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import AppTabBar from '@/components/AppTabBar/AppTabBar.vue'
import { useTabBarPage } from '@/hooks/useTabBarPage'

useTabBarPage(2)

function goHome() {
  uni.switchTab({ url: '/pages/home/index' })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
}

.coming-wrap {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-bottom: 120rpx;
}

.coming-icon {
  width: 160rpx;
  height: 160rpx;
  border-radius: 40rpx;
  background: var(--bg-card);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 40rpx;
}

.coming-title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--text-main);
  margin-bottom: 16rpx;
}

.coming-desc {
  font-size: 26rpx;
  color: var(--text-tertiary);
  margin-bottom: 56rpx;
}

.coming-btn {
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 18rpx 48rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, $mint-primary, $secondary-color);
  color: #fff;
  font-size: 28rpx;
  font-weight: 500;
  box-shadow: 0 8rpx 24rpx rgba(20, 184, 166, 0.3);
  transition: transform 0.15s ease;

  &:active {
    transform: scale(0.95);
  }
}
</style>
