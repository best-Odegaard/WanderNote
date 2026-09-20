<template>
  <view class="empty-state">
    <view class="empty-icon">
      <AppIcon :name="icon" :size="88" color="var(--text-tertiary)" />
    </view>
    <text class="empty-title">{{ title }}</text>
    <text v-if="description" class="empty-desc">{{ description }}</text>
    <button v-if="buttonText" class="empty-btn" @tap="$emit('action')">{{ buttonText }}</button>
  </view>
</template>

<script setup lang="ts">
interface Props {
  icon?: string
  title?: string
  description?: string
  buttonText?: string
}

withDefaults(defineProps<Props>(), {
  icon: 'map',
  title: '暂无数据',
  description: '',
  buttonText: ''
})

defineEmits<{ action: [] }>()
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 72rpx 48rpx 80rpx;
  margin: 16rpx 0 24rpx;
  // 空态也用玻璃卡：整站统一的「通透」语言，避免文字裸飘在页面上
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  border: 1rpx solid var(--glass-border);
  border-radius: $card-radius-lg;
  box-shadow: var(--glass-shadow);
}

.empty-icon {
  margin-bottom: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 152rpx;
  height: 152rpx;
  border-radius: 50%;
  background: var(--brand-grad-soft);
}

.empty-title {
  font-size: 34rpx;
  color: var(--text-main);
  font-weight: 700;
}

.empty-desc {
  font-size: 26rpx;
  color: var(--text-secondary);
  margin-top: 12rpx;
  text-align: center;
}

.empty-btn {
  margin-top: 36rpx;
  background: var(--brand-grad);
  color: var(--on-brand);
  font-size: 28rpx;
  font-weight: 600;
  border-radius: $radius-pill;
  padding: 0 52rpx;
  height: 80rpx;
  line-height: 80rpx;
  box-shadow: var(--brand-glow);
  transition: transform $dur-fast $ease-out;

  &::after { border: none; }

  &:active { transform: scale(0.96); }
}
</style>
