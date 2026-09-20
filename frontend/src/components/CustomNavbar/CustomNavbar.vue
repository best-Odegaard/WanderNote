<template>
  <view
    class="custom-navbar"
    :class="{ 'is-glass': !bgColor }"
    :style="{ paddingTop: statusBarHeight + 'px', background: bgColor || undefined }"
  >
    <view class="navbar-content" :style="{ height: navBarHeight + 'px' }">
      <view v-if="showBack" class="navbar-left" @tap="handleBack">
        <AppIcon name="chevron-left" :size="36" color="var(--text-main)" />
      </view>
      <view class="navbar-title">
        <text>{{ title }}</text>
      </view>
      <view class="navbar-right">
        <slot name="right" />
      </view>
    </view>
  </view>
  <view v-if="fixed" class="navbar-placeholder" :style="{ height: totalHeight + 'px' }" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  title?: string
  showBack?: boolean
  fixed?: boolean
  bgColor?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  showBack: false,
  fixed: true,
  bgColor: ''
})

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const navBarHeight = 44
const totalHeight = statusBarHeight + navBarHeight

function handleBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.switchTab({ url: '/pages/home/index' })
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.custom-navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 999;
}

/*
 * 默认玻璃导航栏：半透明 + 模糊，内容从下方滚过时透出。
 * 页面若显式传了 bgColor，则不加 .is-glass，保留页面自己的实色需求。
 */
.custom-navbar.is-glass {
  background: var(--glass-bg-strong);
  backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  border-bottom: 1rpx solid var(--glass-border);
}

.navbar-content {
  display: flex;
  align-items: center;
  padding: 0 24rpx;
  position: relative;
}

.navbar-left {
  width: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 64rpx;
  border-radius: 50%;
  transition: transform $dur-fast $ease-out;

  &:active {
    transform: scale(0.9);
  }
}

.navbar-title {
  flex: 1;
  text-align: center;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--text-main);
  letter-spacing: -0.3rpx;
}

.navbar-right {
  width: 80rpx;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
</style>
