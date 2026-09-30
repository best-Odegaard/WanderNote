<template>
  <view v-if="question && (chips.length > 0 || canSkip)" class="chips">
    <view class="chips-head" v-if="question.hint">
      <text class="hint">💡 {{ question.hint }}</text>
    </view>

    <view class="chips-row">
      <view
        v-for="opt in chips"
        :key="opt.value"
        class="chip"
        :class="{ disabled }"
        @tap="onSelect(opt)"
      >{{ opt.label }}</view>
    </view>

    <view v-if="canSkip" class="chips-skip" :class="{ disabled }" @tap="onSkip">
      <text>{{ question.skipLabel || '跳过' }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 槽位问题的可点击选项。
 *
 * 产品目标：让用户不需要自己想问题 —— 每个问题都给一排能点的答案，
 * 打字只是备选。所以这里刻意做得「点得到」：选项够大、可换行、跳过永远可见。
 */
import { computed } from 'vue'
import type { SlotOption, SlotQuestion } from '@/api/trip'

const props = defineProps<{
  question: SlotQuestion | null
  /** 正在提交上一轮回答时禁用，避免连点造成状态错乱 */
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'select', slot: string, value: string): void
  (e: 'skip', slot: string): void
}>()

const chips = computed<SlotOption[]>(() => props.question?.options ?? [])
const canSkip = computed(() => props.question?.allowSkip !== false && !!props.question)

function onSelect(opt: SlotOption) {
  if (props.disabled || !props.question) return
  emit('select', props.question.slot, opt.value)
}

function onSkip() {
  if (props.disabled || !props.question) return
  emit('skip', props.question.slot)
}
</script>

<style lang="scss" scoped>
.chips {
  margin-top: 16rpx;
}

.chips-head {
  margin-bottom: 12rpx;
}

.hint {
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
}

.chips-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.chip {
  padding: 20rpx 32rpx;
  background: var(--bg-card);
  border: 2rpx solid var(--border);
  border-radius: 999rpx;
  font-size: var(--fs-body);
  color: var(--text-main);
  line-height: 1.2;
  transition: all 0.15s;

  &:active {
    background: var(--primary-soft);
    border-color: var(--primary-strong);
    color: var(--primary-strong);
  }

  &.disabled {
    opacity: 0.5;
  }
}

.chips-skip {
  display: inline-block;
  margin-top: 16rpx;
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  background: transparent;
  border: 2rpx dashed var(--border);
  font-size: var(--fs-meta);
  color: var(--text-tertiary);

  &:active {
    color: var(--text-secondary);
  }

  &.disabled {
    opacity: 0.5;
  }
}
</style>
