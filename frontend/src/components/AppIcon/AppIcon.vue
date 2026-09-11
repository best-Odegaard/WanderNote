<template>
  <!-- H5 / App 端渲染内联 SVG（线性风格，随 currentColor 适配深浅主题） -->
  <!-- #ifdef H5 || APP-PLUS -->
  <svg
    class="app-icon"
    :width="px"
    :height="px"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    :style="{ color: color, width: px + 'px', height: px + 'px' }"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
  <!-- #endif -->

  <!-- 小程序端降级：SVG 支持受限，回退为 emoji 文字，保证不白屏 -->
  <!-- #ifdef MP-WEIXIN || MP-ALIPAY || MP-BAIDU || MP-TOUTIAO -->
  <text class="app-icon app-icon-fallback" :style="{ fontSize: size + 'rpx', color: color, width: size + 'rpx', height: size + 'rpx', lineHeight: (size + 2) + 'rpx' }">
    {{ fallback }}
  </text>
  <!-- #endif -->
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ICONS } from './icons'

interface Props {
  /** 图标名（见 icons.ts 的 ICONS 字典） */
  name: string
  /** 尺寸，单位 rpx，默认 40rpx */
  size?: number
  /** 颜色，默认 currentColor（跟随文字色，自动适配深浅主题） */
  color?: string
  /** 描边粗细，默认 2 */
  strokeWidth?: number
}

const props = withDefaults(defineProps<Props>(), {
  size: 40,
  color: 'currentColor',
  strokeWidth: 2
})

const icon = computed(() => ICONS[props.name] || ICONS.help)
const paths = computed(() => icon.value.paths)
const fallback = computed(() => icon.value.fallback)

/** rpx → px（H5 下 1rpx = 屏幕宽/750，用 upx2px 换算最准确） */
const px = computed(() => {
  const base = uni.upx2px ? uni.upx2px(750) : 375
  return Math.round((props.size * base) / 750)
})
</script>

<style lang="scss" scoped>
.app-icon {
  display: inline-block;
  flex-shrink: 0;
  vertical-align: middle;
}

.app-icon-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-align: center;
}
</style>
