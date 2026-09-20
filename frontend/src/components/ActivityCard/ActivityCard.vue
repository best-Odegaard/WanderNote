<template>
  <view class="activity-card card" @tap="handleTap">
    <image class="cover" :src="item.cover" mode="aspectFill" />
    <view class="content">
      <text class="title text-ellipsis-2">{{ item.title }}</text>
      <view class="row">
        <AppIcon name="calendar" :size="26" color="var(--text-secondary)" />
        <text class="text">{{ formatDate(item.startTime, 'MM-DD HH:mm') }}</text>
      </view>
      <view class="row">
        <AppIcon name="map-pin" :size="26" color="var(--text-secondary)" />
        <text class="text text-ellipsis">{{ item.location }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { formatDate } from '@/utils/format'
import type { ActivityItem } from '@/api/activity'

interface Props {
  item: ActivityItem
}

const props = defineProps<Props>()

function handleTap() {
  uni.navigateTo({ url: `/pages/activity/detail?id=${props.item.id}` })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.activity-card {
  display: flex;
  gap: 20rpx;
  margin-bottom: 24rpx;
}

.cover {
  width: 200rpx;
  height: 150rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
}

.content {
  flex: 1;
  min-width: 0;
}

.title {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-body);
  margin-bottom: 12rpx;
}

.row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 8rpx;
}

.text {
  font-size: var(--fs-meta);
  color: var(--text-secondary);
  flex: 1;
}
</style>
