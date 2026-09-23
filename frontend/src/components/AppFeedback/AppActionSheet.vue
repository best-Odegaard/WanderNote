<template>
  <!--
    动作面板（替代 uni.showActionSheet 的渲染）。
    与原生行为对齐：点遮罩或取消行走 fail 回调，不触发 success。
  -->
  <view class="fb-sheet-layer">
    <view class="fb-sheet-mask" @tap="onCancel" />
    <view class="fb-sheet-panel safe-bottom">
      <view class="fb-sheet-group">
        <view
          v-for="(item, index) in sheet.items"
          :key="index"
          class="fb-sheet-item"
          @tap="onTap(index)"
        >
          <AppIcon :name="item.icon" :size="40" :color="sheet.itemColor" />
          <text class="fb-sheet-label" :style="{ color: sheet.itemColor }">{{ item.label }}</text>
        </view>
      </view>
      <view class="fb-sheet-cancel" @tap="onCancel">
        <text class="fb-sheet-cancel-label">取消</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { FeedbackSheetState } from '@/store/feedback'
import { cancelSheet, settleSheet } from '@/utils/feedback-custom'
import AppIcon from '../AppIcon/AppIcon.vue'

const props = defineProps<{ sheet: FeedbackSheetState }>()

function onTap(index: number) {
  settleSheet(props.sheet.id, index)
}

function onCancel() {
  cancelSheet(props.sheet.id)
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.fb-sheet-layer {
  position: absolute;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  pointer-events: auto;
}

.fb-sheet-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.fb-sheet-panel {
  position: relative;
  padding: 0 24rpx calc(24rpx + env(safe-area-inset-bottom));
  animation: fb-sheet-in 0.28s $ease-out;
}

@keyframes fb-sheet-in {
  from {
    opacity: 0;
    transform: translateY(40rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.fb-sheet-group {
  border-radius: $radius-lg;
  overflow: hidden;
  background: var(--glass-bg-strong);
  border: 1rpx solid var(--glass-border);
  box-shadow: var(--glass-shadow);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
}

.fb-sheet-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  height: 112rpx;
  padding: 0 36rpx;
  transition: background 0.15s $ease-out;

  & + & {
    border-top: 1rpx solid var(--border);
  }

  &:active {
    background: var(--bg-muted);
  }
}

.fb-sheet-label {
  font-size: var(--fs-body);
  font-weight: 500;
}

.fb-sheet-cancel {
  margin-top: 20rpx;
  height: 112rpx;
  border-radius: $radius-lg;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  box-shadow: var(--glass-shadow);
  transition: transform 0.15s $ease-out;

  &:active {
    transform: scale(0.98);
  }
}

.fb-sheet-cancel-label {
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--text-body);
}
</style>
