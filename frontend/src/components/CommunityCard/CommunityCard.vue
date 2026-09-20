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
  border-radius: $card-radius-lg;
  overflow: hidden;
  break-inside: avoid;
  margin-bottom: 20rpx;
  border: 1rpx solid var(--border);
  box-shadow: var(--shadow-sm);
  transition: transform $dur-fast $ease-out, box-shadow $dur-base $ease-out;

  &:active {
    transform: scale(0.975);
    box-shadow: var(--shadow-xs);
  }
}

.cover {
  width: 100%;
  display: block;
}

.info {
  padding: 20rpx 22rpx 22rpx;
}

.title {
  font-size: 27rpx;
  font-weight: 600;
  color: var(--text-main);
  line-height: 1.45;
}

.author-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 14rpx;
}

.avatar {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  border: 1rpx solid var(--border);
}

.nickname {
  font-size: 22rpx;
  color: var(--text-secondary);
  flex: 1;
}

.stats {
  display: flex;
  gap: 22rpx;
  margin-top: 12rpx;
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
  font-weight: 500;
  color: #fff;
  background: rgba(230, 67, 64, 0.92);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  padding: 6rpx 20rpx;
  border-radius: $radius-pill;
  box-shadow: var(--shadow-sm);
}
</style>
