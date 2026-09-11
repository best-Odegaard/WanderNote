<template>
  <view class="plan-card" :class="{ ended: plan.status === 'ended' }" @tap="$emit('tap')">
    <view v-if="plan.status === 'ended'" class="status-tag">
      <AppIcon name="box" :size="22" color="var(--text-secondary)" />
      <text>已结束</text>
    </view>
    <view class="plan-left">
      <text class="plan-title font-hand">{{ plan.title }}</text>
      <view class="plan-meta">
        <view class="meta-line">
          <text>{{ plan.days }}天{{ plan.nights }}晚</text>
        </view>
        <view class="meta-line">
          <text>{{ plan.placeCount }}个地点</text>
        </view>
      </view>
      <view class="collab">
        <image v-if="plan.avatar" class="avatar" :src="plan.avatar" mode="aspectFill" />
        <view class="add-btn">+</view>
      </view>
    </view>
    <image class="plan-thumb" :src="plan.cover" mode="aspectFill" />
  </view>
</template>

<script setup lang="ts">
export interface MyPlanItem {
  id: number | string
  title: string
  days: number
  nights: number
  placeCount: number
  cover: string
  avatar?: string
  status?: 'active' | 'ended'
}

defineProps<{ plan: MyPlanItem }>()
defineEmits<{ tap: [] }>()
</script>

<style lang="scss" scoped>
.plan-card {
  position: relative;
  display: flex;
  align-items: stretch;
  background: var(--bg-mint-soft);
  border-radius: 32rpx;
  padding: 32rpx 28rpx;
  margin-bottom: 24rpx;
  min-height: 200rpx;
  overflow: visible;

  &:active {
    transform: scale(0.99);
  }
}

.status-tag {
  position: absolute;
  top: 20rpx;
  left: 24rpx;
  font-size: 22rpx;
  color: var(--text-secondary);
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
}

.plan-left {
  flex: 1;
  min-width: 0;
  padding-right: 120rpx;
}

.plan-title {
  font-size: 36rpx;
  color: var(--text-main);
  line-height: 1.35;
  display: block;
}

.plan-meta {
  margin-top: 20rpx;
  padding-left: 16rpx;
  border-left: 4rpx solid var(--border);
}

.meta-line {
  font-size: 26rpx;
  color: var(--text-secondary);
  line-height: 1.6;
}

.collab {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 24rpx;
}

.avatar {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  border: 2rpx solid #fff;
}

.add-btn {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: var(--bg-card);
  border: 2rpx dashed var(--text-tertiary);
  text-align: center;
  line-height: 44rpx;
  font-size: 28rpx;
  color: var(--text-secondary);
}

.plan-thumb {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%) rotate(6deg);
  width: 160rpx;
  height: 160rpx;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.12);
}
</style>
