<template>
  <view class="scenic-card" @tap="handleTap">
    <image class="cover" :src="item.cover" mode="aspectFill" />
    <view class="info">
      <text class="name text-ellipsis">{{ item.name }}</text>
      <view class="meta">
        <view class="rating">
          <AppIcon name="star" :size="22" color="#f59e0b" />
          <text>{{ formatRating(item.rating) }}</text>
        </view>
        <text class="city">{{ item.city }}</text>
      </view>
      <text v-if="item.price !== undefined" class="price">{{ formatPrice(item.price) }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { formatRating, formatPrice } from '@/utils/format'
import type { ScenicItem } from '@/api/scenic'

interface Props {
  item: ScenicItem
}

const props = defineProps<Props>()

function handleTap() {
  uni.navigateTo({ url: `/pages/scenic/detail?id=${props.item.id}` })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.scenic-card {
  width: 280rpx;
  flex-shrink: 0;
  background: var(--bg-card);
  border-radius: $card-radius;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
}

.cover {
  width: 100%;
  height: 200rpx;
}

.info {
  padding: 16rpx;
}

.name {
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-body);
  display: block;
}

.meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 8rpx;
}

.rating {
  display: inline-flex;
  align-items: center;
  gap: 4rpx;
  font-size: var(--fs-meta);
  color: $warning-color;
}

.city {
  font-size: var(--fs-meta);
  color: var(--text-secondary);
}

.price {
  font-size: var(--fs-meta);
  color: $error-color;
  margin-top: 8rpx;
  display: block;
}
</style>
