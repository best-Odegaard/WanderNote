<template>
  <view v-if="questions.length > 0" class="suggest">
    <text class="suggest-label">猜你想问</text>
    <view class="suggest-row">
      <view
        v-for="q in questions"
        :key="q"
        class="suggest-chip"
        :class="{ disabled }"
        @tap="onPick(q)"
      >{{ q }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 「猜你想问」。
 *
 * 用户聊完一轮常常"还想问点什么但组织不出来"，让他对着空白输入框硬想就是流失点。
 * 这里给 2-3 个具体到可以直接发出去的追问，点一下就等于他把这句话打出来了。
 *
 * 与 ChatChips 的区别（两者会同时出现，别混）：
 *   ChatChips  = 槽位问题，点一下是**填参数**（去哪、几天），不调模型
 *   本组件      = 话题建议，点一下是**发一句话**，走正常对话链路
 * 所以样式上刻意做得比 ChatChips 轻（浅底描边、带「猜你想问」标签），
 * 避免用户分不清哪个是"必须答的"、哪个是"可以聊的"。
 */
import { computed } from 'vue'

const props = defineProps<{
  questions: string[]
  /** 正在发送或生成中时禁用，避免连点 */
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'pick', question: string): void
}>()

const questions = computed(() => props.questions || [])

function onPick(q: string) {
  if (props.disabled) return
  emit('pick', q)
}
</script>

<style lang="scss" scoped>
.suggest {
  margin-top: 28rpx;
  padding-top: 20rpx;
  border-top: 1rpx dashed var(--border);
}

.suggest-label {
  display: block;
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
  margin-bottom: 14rpx;
}

.suggest-row {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.suggest-chip {
  padding: 14rpx 26rpx;
  border-radius: 999rpx;
  background: transparent;
  border: 2rpx solid var(--border);
  font-size: var(--fs-meta);
  color: var(--text-secondary);
  line-height: 1.2;
  transition: all 0.15s;

  &:active {
    background: var(--bg-muted);
    color: var(--text-main);
  }

  &.disabled {
    opacity: 0.45;
  }
}
</style>
