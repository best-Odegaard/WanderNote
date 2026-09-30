<template>
  <view class="plan-card" :style="{ background: bgColor }" @tap="$emit('tap')">
    <!-- 状态徽章：待出行 / 进行中 / 已结束 -->
    <view class="status-badge">
      <AppIcon :name="statusMeta.icon" :size="22" :color="statusMeta.color" />
      <text class="status-text" :style="{ color: statusMeta.color }">{{ statusMeta.label }}</text>
    </view>

    <view class="plan-body">
      <text class="plan-title">{{ plan.title }}</text>

      <view class="plan-meta">
        <view class="meta-line">
          <text>{{ metaFirstLine }}</text>
        </view>
        <view class="meta-line">
          <text>{{ plan.placeCount }}个地点</text>
        </view>
      </view>

      <view class="plan-owner">
        <image v-if="plan.avatar" class="avatar" :src="plan.avatar" mode="aspectFill" />
        <view v-else class="avatar avatar-placeholder">
          <AppIcon name="user" :size="26" color="var(--text-tertiary)" />
        </view>
      </view>

      <!--
        从行程列表进入「该行程的对话」。
        原来这个入口是行程列表自己画在卡片上的，首页的行程卡片搬过来后合并到这里，
        保证迁移不丢能力。
      -->
      <view v-if="showChatEntry" class="plan-chat-btn" @tap.stop="$emit('chat')">
        <text>进入行程对话</text>
      </view>
    </view>

    <!-- 右侧缩略图：斜放并向右溢出，多余部分被卡片裁掉；无图时用同尺寸占位保持版式一致 -->
    <image v-if="plan.cover" class="plan-thumb" :src="plan.cover" mode="aspectFill" />
    <view v-else class="plan-thumb plan-thumb-empty">
      <AppIcon name="map-pin" :size="44" color="var(--text-tertiary)" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon/AppIcon.vue'
import { tripCardBg, type MyPlanItem, type TripCardStatus } from '@/utils/tripCard'

interface Props {
  plan: MyPlanItem
  /** 列表内序号，用于底色按序循环 */
  index?: number
  /** 是否显示「进入行程对话」入口（行程绑定了 AI 会话时才显示） */
  showChatEntry?: boolean
}

const props = withDefaults(defineProps<Props>(), { index: 0, showChatEntry: false })

defineEmits<{ tap: []; chat: [] }>()

const bgColor = computed(() => tripCardBg(props.index))

/** 状态 → 徽章图标 / 文案 / 颜色 */
const STATUS_META: Record<TripCardStatus, { icon: string; label: string; color: string }> = {
  pending: { icon: 'calendar', label: '待出行', color: 'var(--trip-pending)' },
  ongoing: { icon: 'clock', label: '进行中', color: 'var(--trip-ongoing)' },
  ended: { icon: 'box', label: '已结束', color: 'var(--trip-ended)' }
}

const statusMeta = computed(() => STATUS_META[props.plan.status] ?? STATUS_META.pending)

/** 首行信息：有日期区间时与天数并排，如「11.26至11.29 4天3晚」；1 天行程不写「0晚」 */
const metaFirstLine = computed(() => {
  const { days, nights, dateRange } = props.plan
  const duration = nights > 0 ? `${days}天${nights}晚` : `${days}天`
  return dateRange ? `${dateRange} ${duration}` : duration
})
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.plan-card {
  position: relative;
  // 裁掉向右溢出的缩略图，同时保证圆角
  overflow: hidden;
  border-radius: 32rpx;
  padding: 28rpx 32rpx 26rpx;
  margin-bottom: 24rpx;
  min-height: 220rpx;

  &:active {
    opacity: 0.92;
  }
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 6rpx 18rpx;
  border-radius: 999rpx;
  background: var(--trip-badge-bg);
  margin-bottom: 14rpx;
}

.status-text {
  font-size: var(--fs-caption);
  font-weight: 500;
}

.plan-body {
  position: relative;
  z-index: 2;
  // 给右侧缩略图留出空间：可见宽 210-52=158rpx，再留一点间隙
  padding-right: 172rpx;
}

.plan-title {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  font-size: var(--fs-subhead);
  line-height: 1.3;
  color: var(--text-main);
  font-weight: 700;
}

.plan-meta {
  margin-top: 16rpx;
  padding-left: 16rpx;
  border-left: 4rpx solid var(--trip-bar);
}

.meta-line {
  font-size: var(--fs-meta);
  color: var(--text-secondary);
  line-height: 1.6;
}

.plan-owner {
  display: flex;
  align-items: center;
  margin-top: 18rpx;
}

.avatar {
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  border: 2rpx solid rgba(255, 255, 255, 0.85);
}

.avatar-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--trip-badge-bg);
  border-color: transparent;
}

/* 进入行程对话：与卡片同色系的浅底胶囊，不抢卡片本身的点击（stop 阻止冒泡） */
.plan-chat-btn {
  display: inline-flex;
  align-items: center;
  margin-top: 20rpx;
  padding: 12rpx 26rpx;
  border-radius: 999rpx;
  background: var(--trip-badge-bg);
  font-size: var(--fs-caption);
  color: var(--text-main);

  &:active {
    opacity: 0.7;
  }
}

/* 缩略图：向右溢出 52rpx（= 210 的 1/4），即露出四分之三，另外四分之一被卡片右缘裁掉。
   左缘落在 528rpx 处，卡片中线是 343rpx（卡宽 686rpx），不会越过中线。 */
.plan-thumb {
  position: absolute;
  z-index: 1;
  right: -52rpx;
  top: 50%;
  width: 210rpx;
  height: 210rpx;
  border-radius: 26rpx;
  transform: translateY(-50%) rotate(6deg);
  box-shadow: 0 10rpx 28rpx rgba(0, 0, 0, 0.16);
}

/* 无封面时的占位：尺寸与位置跟缩略图一致，保证有无图卡片版式统一 */
.plan-thumb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--trip-badge-bg);
  box-shadow: none;
}
</style>
