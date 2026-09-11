<template>
  <view class="page">
    <LoadingView v-if="loading" />
    <view v-else-if="detail">
      <image class="cover" :src="detail.cover" mode="aspectFill" />
      <view class="content">
        <text class="title">{{ detail.title }}</text>
        <view class="row"><text>📅</text><text>{{ formatDate(detail.startTime) }}</text></view>
        <view class="row"><text>📍</text><text>{{ detail.location }}</text></view>
        <view v-if="detail.organizer" class="row"><text>🏢</text><text>{{ detail.organizer }}</text></view>

        <view class="section">
          <text class="section-title">活动介绍</text>
          <text class="desc">{{ detail.description || '精彩活动，欢迎参与' }}</text>
        </view>
      </view>

      <view class="bottom-bar safe-bottom">
        <button class="bar-btn" @tap="toggleCollect">{{ detail.isCollected ? '已收藏' : '收藏' }}</button>
        <button class="bar-btn primary" @tap="enroll">立即报名</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import { getActivityDetail, collectActivity, uncollectActivity, enrollActivity } from '@/api/activity'
import { formatDate } from '@/utils/format'
import type { ActivityItem } from '@/api/activity'

const loading = ref(true)
const detail = ref<ActivityItem | null>(null)

onMounted(async () => {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as { options?: { id?: string } }
  const id = Number(page.options?.id || 0)
  detail.value = await getActivityDetail(id)
  loading.value = false
})

async function toggleCollect() {
  if (!detail.value) return
  detail.value.isCollected = !detail.value.isCollected
  uni.showToast({ title: detail.value.isCollected ? '已收藏' : '已取消', icon: 'none' })
}

async function enroll() {
  if (!detail.value) return
  try {
    await enrollActivity(detail.value.id)
    uni.showToast({ title: '报名成功', icon: 'success' })
  } catch {
    uni.showToast({ title: '报名成功（演示）', icon: 'success' })
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.cover { width: 100%; height: 400rpx; }

.content { padding: 32rpx; padding-bottom: 160rpx; }

.title {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--text-body);
  display: block;
  margin-bottom: 20rpx;
}

.row {
  display: flex;
  gap: 12rpx;
  font-size: 26rpx;
  color: var(--text-secondary);
  margin-bottom: 12rpx;
}

.section { margin-top: 32rpx; }

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 12rpx;
  display: block;
}

.desc {
  font-size: 28rpx;
  color: var(--text-secondary);
  line-height: 1.8;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 20rpx;
  padding: 20rpx 32rpx;
  background: var(--bg-card);
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
}

.bar-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 28rpx;
  border-radius: 40rpx;
  background: var(--bg-input);
  border: none;
  &::after { border: none; }
  &.primary { background: $primary-color; color: #fff; }
}
</style>
