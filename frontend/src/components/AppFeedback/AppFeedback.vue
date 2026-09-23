<template>
  <view class="fb-root">
    <!-- 轻提示：玻璃胶囊，三态图标，长文案自动换行不截断 -->
    <view v-if="toast" class="fb-toast-layer">
      <view class="fb-toast" :class="{ 'fb-toast--has-icon': toast.icon !== 'none' }">
        <view v-if="toast.icon === 'loading'" class="fb-spinner fb-spinner--sm" />
        <view v-else-if="toast.icon === 'success'" class="fb-toast-badge fb-toast-badge--success">
          <text class="fb-toast-glyph">✓</text>
        </view>
        <view v-else-if="toast.icon === 'error'" class="fb-toast-badge fb-toast-badge--error">
          <text class="fb-toast-glyph">✕</text>
        </view>
        <text class="fb-toast-text">{{ toast.title }}</text>
      </view>
    </view>

    <!-- 全局 loading：计数由 utils/request.ts 维护，遮罩挡触摸与原生 mask 一致 -->
    <view v-if="loadingCount > 0" class="fb-loading-mask">
      <view class="fb-loading-card">
        <view class="fb-spinner" />
        <text class="fb-loading-title">{{ loadingTitle }}</text>
      </view>
    </view>

    <AppConfirm v-if="modal" :modal="modal" />
    <AppActionSheet v-if="sheet" :sheet="sheet" />
  </view>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useFeedbackStore } from '@/store/feedback'
import AppConfirm from './AppConfirm.vue'
import AppActionSheet from './AppActionSheet.vue'

const store = useFeedbackStore()
const { toast, modal, sheet, loadingCount, loadingTitle } = storeToRefs(store)

let toastTimer: ReturnType<typeof setTimeout> | null = null

watch(
  toast,
  (value) => {
    if (toastTimer) {
      clearTimeout(toastTimer)
      toastTimer = null
    }
    if (value) {
      toastTimer = setTimeout(() => store.setToast(null), value.duration)
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (toastTimer) clearTimeout(toastTimer)
})

/** 确认弹窗 / 动作面板打开时锁定 H5 背景滚动（原生弹窗自带该行为） */
const scrollLocked = computed(() => !!modal.value || !!sheet.value)
let savedOverflow: { html: string; body: string } | null = null

watch(
  scrollLocked,
  (locked) => {
    // #ifdef H5
    try {
      if (locked) {
        if (savedOverflow) return
        savedOverflow = {
          html: document.documentElement.style.overflow,
          body: document.body.style.overflow
        }
        document.documentElement.style.overflow = 'hidden'
        document.body.style.overflow = 'hidden'
      } else if (savedOverflow) {
        document.documentElement.style.overflow = savedOverflow.html
        document.body.style.overflow = savedOverflow.body
        savedOverflow = null
      }
    } catch {
      /* 非浏览器环境忽略 */
    }
    // #endif
  },
  { immediate: true }
)
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

/* 全局层：不拦截页面触摸，只有各弹层自身处理事件 */
.fb-root {
  position: fixed;
  inset: 0;
  z-index: 9999;
  pointer-events: none;
}

/* ── Toast ── */
.fb-toast-layer {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  pointer-events: none;
}

.fb-toast {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  max-width: 600rpx;
  padding: 24rpx 36rpx;
  border-radius: $radius-pill;
  background: var(--glass-bg-strong);
  border: 1rpx solid var(--glass-border);
  box-shadow: var(--glass-shadow);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  animation: fb-toast-in 0.22s $ease-spring;
}

.fb-toast--has-icon {
  padding-left: 28rpx;
}

.fb-toast-text {
  font-size: var(--fs-body);
  color: var(--text-main);
  line-height: 1.5;
  text-align: center;
}

.fb-toast-badge {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &--success {
    background: var(--brand-soft);
  }

  &--error {
    background: var(--error-bg);
  }
}

.fb-toast-glyph {
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1;

  .fb-toast-badge--success & {
    color: var(--brand-ink);
  }

  .fb-toast-badge--error & {
    color: var(--danger);
  }
}

@keyframes fb-toast-in {
  from {
    opacity: 0;
    transform: scale(0.92);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

/* ── Loading ── */
.fb-loading-mask {
  position: absolute;
  inset: 0;
  z-index: 4000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.25);
  pointer-events: auto;
}

.fb-loading-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
  min-width: 220rpx;
  padding: 40rpx 48rpx;
  border-radius: $radius-lg;
  background: var(--glass-bg-strong);
  border: 1rpx solid var(--glass-border);
  box-shadow: var(--glass-shadow);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  animation: fb-toast-in 0.22s $ease-spring;
}

.fb-loading-title {
  font-size: var(--fs-body);
  color: var(--text-main);
}

/* 转圈：品牌色渐变弧，与玻璃拟态主题一致 */
.fb-spinner {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  border: 5rpx solid var(--brand-soft);
  border-top-color: var(--brand);
  box-sizing: border-box;
  animation: fb-spin 0.8s linear infinite;

  &--sm {
    width: 36rpx;
    height: 36rpx;
    border-width: 4rpx;
  }
}

@keyframes fb-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
