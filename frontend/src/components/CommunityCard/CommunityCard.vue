<template>
  <view class="community-card" @tap="handleTap">
    <image class="cover" :src="item.cover" mode="widthFix" />
    <view class="info">
      <text class="title text-ellipsis-2">{{ item.title }}</text>
      <view class="author-row">
        <image class="avatar" :src="item.author.avatar" mode="aspectFill" />
        <text class="nickname text-ellipsis">{{ item.author.nickname }}</text>
      </view>
      <view class="stats">
        <view class="stat">
          <AppIcon name="heart" :size="22" color="var(--text-tertiary)" />
          <text>{{ formatCount(item.likeCount) }}</text>
        </view>
        <view class="stat">
          <AppIcon name="star" :size="22" color="var(--text-tertiary)" />
          <text>{{ formatCount(item.collectCount) }}</text>
        </view>
      </view>
      <text v-if="showDelete" class="card-delete" @tap.stop="handleDelete">删除</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { formatCount } from '@/utils/format'
import type { CommunityPost } from '@/api/community'

interface Props {
  item: CommunityPost
  /** 是否显示删除按钮（仅本人游记列表使用） */
  showDelete?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{ (e: 'delete', item: CommunityPost): void }>()

function handleTap() {
  uni.navigateTo({ url: `/pages/community/detail?id=${props.item.id}` })
}

function handleDelete() {
  emit('delete', props.item)
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.community-card {
  position: relative;
  background: var(--bg-card);
  border-radius: $card-radius;
  overflow: hidden;
  break-inside: avoid;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 10rpx rgba(88, 168, 131, 0.08);
}

.cover {
  width: 100%;
  display: block;
}

.info {
  padding: 16rpx;
}

.title {
  font-size: 26rpx;
  font-weight: 500;
  color: var(--text-main);
  line-height: 1.4;
}

.author-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 12rpx;
}

.avatar {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
}

.nickname {
  font-size: 22rpx;
  color: var(--text-secondary);
  flex: 1;
}

.stats {
  display: flex;
  gap: 20rpx;
  margin-top: 8rpx;
}

.stat {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  font-size: 22rpx;
  color: var(--text-tertiary);
}

.card-delete {
  position: absolute;
  right: 16rpx;
  bottom: 16rpx;
  font-size: 22rpx;
  color: var(--danger);
  background: var(--bg-card);
  border: 2rpx solid var(--danger);
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
}
</style>
