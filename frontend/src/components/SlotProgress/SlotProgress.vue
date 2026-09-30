<template>
  <view v-if="visible" class="slot-progress">
    <view class="bar">
      <view class="bar-fill" :style="{ width: percent + '%' }" />
    </view>
    <text class="label">{{ label }}</text>
  </view>
</template>

<script setup lang="ts">
/**
 * 需求完整度进度条。
 *
 * 作用不是「好看」，而是给用户一个「再答两题就能出完整方案」的预期，
 * 让点击式追问不显得没完没了。
 */
import { computed } from 'vue'

const props = defineProps<{
  /** 0~1 */
  completeness: number
}>()

/** 一项都没答时不显示，避免空进度条带来「又要填一堆」的错觉 */
const visible = computed(() => (props.completeness ?? 0) > 0)

const percent = computed(() => Math.round(Math.min(1, Math.max(0, props.completeness ?? 0)) * 100))

const label = computed(() => {
  const p = percent.value
  if (p >= 90) return '信息齐了，可以直接生成完整路线'
  if (p >= 60) return '信息比较充分，可以生成完整方案'
  if (p >= 30) return '已了解一部分，继续点几下就好'
  return '正在了解你的需求'
})
</script>

<style lang="scss" scoped>
.slot-progress {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 8rpx 0;
}

.bar {
  flex: 0 0 160rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: var(--bg-muted, rgba(0, 0, 0, 0.06));
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 999rpx;
  background: var(--primary-strong, #4a9ef5);
  transition: width 0.3s ease;
}

.label {
  flex: 1;
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
}
</style>
