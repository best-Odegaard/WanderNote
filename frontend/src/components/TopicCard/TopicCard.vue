<template>
  <view class="topic-card" @tap="$emit('tap')">
    <image class="cover" :src="topic.cover" mode="aspectFill" />
    <view class="tag">{{ topic.tag }}</view>
    <view class="overlay">
      <text class="title font-hand">{{ topic.title }}</text>
      <text class="count">{{ topic.placeCount }}个地点</text>
    </view>
  </view>
</template>

<script setup lang="ts">
export interface TopicItem {
  id: number
  tag: string
  title: string
  placeCount: number
  cover: string
}

defineProps<{ topic: TopicItem }>()
defineEmits<{ tap: [] }>()
</script>

<style lang="scss" scoped>
.topic-card {
  position: relative;
  width: 280rpx;
  height: 380rpx;
  border-radius: 28rpx;
  overflow: hidden;
  flex-shrink: 0;
  margin-right: 20rpx;

  &:active {
    transform: scale(0.98);
  }
}

.cover {
  width: 100%;
  height: 100%;
}

.tag {
  position: absolute;
  top: 16rpx;
  left: 16rpx;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 22rpx;
  padding: 8rpx 16rpx;
  border-radius: 20rpx;
}

.overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 48rpx 20rpx 24rpx;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.65));
}

.title {
  font-size: 32rpx;
  color: #fff;
  display: block;
  line-height: 1.3;
}

.count {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 8rpx;
  display: block;
}
</style>
