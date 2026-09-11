<template>
  <view class="tour-card card" @tap="handleTap">
    <view class="header">
      <text class="title text-ellipsis">{{ trip.title }}</text>
      <text class="route">{{ trip.fromCity }} → {{ trip.toCity }}</text>
    </view>
    <view class="meta">
      <text class="tag">{{ trip.days }}天</text>
      <text class="tag">{{ trip.people }}人</text>
      <text class="tag budget">预算 {{ formatPrice(trip.budget) }}</text>
    </view>
    <view v-if="trip.tags?.length" class="tags">
      <text v-for="tag in trip.tags.slice(0, 3)" :key="tag" class="interest-tag">{{ tag }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { formatPrice } from '@/utils/format'
import type { TripPlan } from '@/api/trip'

interface Props {
  trip: TripPlan
}

const props = defineProps<Props>()

function handleTap() {
  if (props.trip.id) {
    uni.navigateTo({ url: `/pages/trip/detail?id=${props.trip.id}` })
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.tour-card {
  margin-bottom: 24rpx;
}

.header {
  margin-bottom: 16rpx;
}

.title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-body);
  display: block;
}

.route {
  font-size: 24rpx;
  color: var(--text-secondary);
  margin-top: 8rpx;
  display: block;
}

.meta {
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}

.tag {
  font-size: 22rpx;
  padding: 4rpx 16rpx;
  background: rgba($primary-color, 0.1);
  color: $primary-color;
  border-radius: 8rpx;

  &.budget {
    background: rgba($secondary-color, 0.1);
    color: $secondary-color;
  }
}

.tags {
  display: flex;
  gap: 8rpx;
  margin-top: 16rpx;
  flex-wrap: wrap;
}

.interest-tag {
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  background: var(--bg-page);
  color: var(--text-secondary);
  border-radius: 6rpx;
}
</style>
