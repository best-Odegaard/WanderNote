<template>
  <view class="loading-view">
    <view class="spinner" />
    <text class="loading-text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
interface Props {
  text?: string
}

withDefaults(defineProps<Props>(), {
  text: '加载中...'
})
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.loading-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 0;
}

.spinner {
  width: 60rpx;
  height: 60rpx;
  border: 4rpx solid var(--border);
  border-top-color: $primary-color;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-text {
  margin-top: 24rpx;
  font-size: var(--fs-body);
  color: var(--text-secondary);
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
