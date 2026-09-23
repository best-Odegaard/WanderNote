<template>
  <!--
    确认弹窗（替代 uni.showModal 的渲染）。
    与原生行为对齐：点遮罩不关闭、系统返回键不关闭；
    editable 模式渲染内嵌输入框，确认时回传原始输入内容（由调用方自行 trim）。
  -->
  <view class="fb-confirm-layer">
    <view class="fb-confirm-mask" />
    <view class="fb-confirm-card" :class="{ 'fb-confirm-card--danger': modal.danger }">
      <view v-if="modal.danger" class="fb-confirm-icon">
        <AppIcon name="alert" :size="44" color="var(--danger)" />
      </view>
      <text class="fb-confirm-title">{{ modal.title }}</text>
      <text v-if="modal.content" class="fb-confirm-content">{{ modal.content }}</text>
      <input
        v-if="modal.editable"
        class="fb-confirm-input"
        :value="inputValue"
        :placeholder="modal.placeholderText"
        placeholder-class="fb-confirm-input-ph"
        confirm-type="done"
        @input="onInput"
      />
      <view class="fb-confirm-actions" :class="{ 'fb-confirm-actions--single': !modal.showCancel }">
        <view v-if="modal.showCancel" class="fb-confirm-btn fb-confirm-btn--cancel" @tap="onCancel">
          <text class="fb-confirm-btn-text" :style="{ color: modal.cancelColor }">{{ modal.cancelText }}</text>
        </view>
        <view
          class="fb-confirm-btn fb-confirm-btn--confirm"
          :class="{ 'fb-confirm-btn--danger': modal.danger }"
          :style="confirmStyle"
          @tap="onConfirm"
        >
          <text class="fb-confirm-btn-text fb-confirm-btn-text--confirm">{{ modal.confirmText }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { FeedbackModalState } from '@/store/feedback'
import { settleModal } from '@/utils/feedback-custom'
import AppIcon from '../AppIcon/AppIcon.vue'

const props = defineProps<{ modal: FeedbackModalState }>()

const inputValue = ref('')

// 确认按钮底色：危险操作用危险色，其余用品牌渐变；confirmColor 非危险色时透传
const confirmStyle = computed(() => {
  if (props.modal.danger) return { background: 'var(--danger)' }
  if (props.modal.confirmColor) return { background: props.modal.confirmColor }
  return { background: 'var(--brand-grad)' }
})

function onInput(e: any) {
  inputValue.value = e?.detail?.value ?? ''
}

function onConfirm() {
  settleModal(props.modal.id, {
    confirm: true,
    cancel: false,
    ...(props.modal.editable ? { content: inputValue.value } : {})
  })
}

function onCancel() {
  settleModal(props.modal.id, {
    confirm: false,
    cancel: true,
    ...(props.modal.editable ? { content: inputValue.value } : {})
  })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.fb-confirm-layer {
  position: absolute;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: auto;
}

.fb-confirm-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.fb-confirm-card {
  position: relative;
  width: 600rpx;
  padding: 48rpx 40rpx 32rpx;
  border-radius: $radius-2xl;
  background: var(--bg-card);
  box-shadow: 0 16rpx 48rpx rgba(15, 118, 110, 0.18);
  display: flex;
  flex-direction: column;
  align-items: center;
  animation: fb-card-in 0.24s $ease-spring;
}

@keyframes fb-card-in {
  from {
    opacity: 0;
    transform: scale(0.94);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.fb-confirm-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: var(--error-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20rpx;
}

.fb-confirm-title {
  font-size: var(--fs-heading);
  font-weight: 600;
  color: var(--text-main);
  text-align: center;
  line-height: 1.4;
}

.fb-confirm-content {
  margin-top: 16rpx;
  font-size: var(--fs-body);
  color: var(--text-body);
  text-align: center;
  line-height: 1.6;
}

.fb-confirm-input {
  margin-top: 28rpx;
  width: 100%;
  height: 88rpx;
  padding: 0 28rpx;
  border-radius: $radius-md;
  background: var(--bg-input);
  font-size: var(--fs-body);
  color: var(--text-main);
  box-sizing: border-box;
}

.fb-confirm-input-ph {
  color: var(--text-tertiary);
}

.fb-confirm-actions {
  margin-top: 36rpx;
  width: 100%;
  display: flex;
  gap: 20rpx;

  &--single {
    .fb-confirm-btn--confirm {
      flex: 1;
    }
  }
}

.fb-confirm-btn {
  flex: 1;
  height: 88rpx;
  border-radius: $radius-pill;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s $ease-out;

  &:active {
    transform: scale(0.97);
  }
}

.fb-confirm-btn--cancel {
  background: var(--bg-muted);
}

.fb-confirm-btn--confirm {
  box-shadow: var(--brand-glow);
}

.fb-confirm-btn--danger {
  box-shadow: none;
}

.fb-confirm-btn-text {
  font-size: var(--fs-body);
  font-weight: 600;
}

.fb-confirm-btn-text--confirm {
  color: #ffffff;
}
</style>
