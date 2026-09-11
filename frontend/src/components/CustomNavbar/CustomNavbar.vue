<template>
  <view class="custom-navbar" :style="{ paddingTop: statusBarHeight + 'px', background: bgColor || 'var(--bg-card)' }">
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
}

.navbar-title {
  flex: 1;
  text-align: center;
  font-size: 34rpx;
  font-weight: 600;
  color: var(--text-main);
}

.navbar-right {
  width: 80rpx;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
</style>
