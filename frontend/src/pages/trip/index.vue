<template>
  <view class="page page-with-tabbar">
    <view class="header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <text class="title">我的行程</text>
    </view>

    <scroll-view scroll-y class="scroll-body" :style="{ paddingTop: headerHeight + 'px' }">
      <LoadingView v-if="loading" />

      <view v-else-if="trips.length === 0" class="empty soft-shadow" @tap="goSurvey">
        <AppIcon name="calendar" :size="96" color="var(--text-tertiary)" />
        <text class="empty-title">还没有行程</text>
        <text class="empty-hint">点击底部 + 创建行程</text>
      </view>

      <view v-else class="trip-list">
        <view
          v-for="trip in trips"
          :key="String(trip.id)"
          class="trip-card soft-shadow"
          @tap="viewTrip(trip)"
        >
          <view class="trip-head">
            <text class="trip-title">{{ trip.title }}</text>
            <AppIcon name="chevron-right" :size="32" color="var(--text-body)" class="trip-arrow" />
          </view>
          <text class="trip-meta">{{ trip.toCity }} · {{ trip.days }}天 · {{ trip.people }}人</text>
          <text class="trip-date">{{ tripDateText(trip) }}</text>
          <view v-if="trip.chatSessionId" class="trip-chat-btn" @tap.stop="openTripChat(trip)">
            <text>进入行程对话</text>
          </view>
        </view>
      </view>

      <view style="height: 160rpx" />
    </scroll-view>

    <!-- #ifndef MP-WEIXIN -->
    <AppTabBar />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import AppTabBar from '@/components/AppTabBar/AppTabBar.vue'
import { useTabBarPage } from '@/hooks/useTabBarPage'
import { useTripStore } from '@/store/trip'
import type { TripPlan } from '@/api/trip'

const tripStore = useTripStore()

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const headerHeight = statusBarHeight + 88

const trips = ref<TripPlan[]>([])
const loading = ref(true)

useTabBarPage(1, loadTrips)

onMounted(() => loadTrips())

async function loadTrips() {
  loading.value = true
  try {
    const list = await tripStore.loadHistory()
    trips.value = Array.isArray(list) ? list : []
  } catch (e) {
    console.warn('加载我的行程失败:', e)
    trips.value = []
  } finally {
    loading.value = false
  }
}

function viewTrip(trip: TripPlan) {
  uni.navigateTo({ url: `/pages/trip/detail?id=${trip.id}` })
}

function openTripChat(trip: TripPlan) {
  if (!trip.chatSessionId) return
  uni.navigateTo({ url: `/pages/ai/chat?tripId=${encodeURIComponent(String(trip.id))}` })
}

/**
 * 卡片上的日期文案。
 * 优先显示真实的出行日期区间；早于本次改动的老行程没有出行日期，退回显示创建时间。
 * 注意：后端 VO 给的字段是 createTime，历史上这里读的是 createdAt，永远取不到值，
 * 于是全都被兜底成写死的 '2026-05-01' —— 那是个假日期，已去掉。
 */
function tripDateText(t: TripPlan) {
  if (t.startDate && t.endDate) {
    return t.startDate === t.endDate ? t.startDate : `${t.startDate} ~ ${t.endDate}`
  }
  if (t.startDate) return t.startDate
  const created = t.createTime || t.createdAt
  if (!created) return '未设置出行日期'
  return `创建于 ${String(created).slice(0, 10)}`
}

function goSurvey() {
  uni.navigateTo({ url: '/pages/plan/wizard' })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
}

.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: var(--bg-page);
  padding: 0 32rpx 24rpx;
}

.title {
  font-size: 44rpx;
  font-weight: 700;
  color: var(--text-body);
  line-height: 88rpx;
}

.scroll-body {
  height: 100vh;
  padding: 0 32rpx;
  box-sizing: border-box;
}

.empty {
  margin-top: 80rpx;
  padding: 80rpx 40rpx;
  background: var(--bg-card);
  border-radius: $card-radius-lg;
  text-align: center;

  .app-icon {
    margin-bottom: 8rpx;
  }
}

.empty-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-body);
  margin-top: 24rpx;
  display: block;
}

.empty-hint {
  font-size: 26rpx;
  color: var(--text-secondary);
  margin-top: 12rpx;
  display: block;
}

.trip-list {
  padding-top: 16rpx;
}

.trip-card {
  background: var(--bg-card);
  border-radius: $card-radius-lg;
  padding: 32rpx;
  margin-bottom: 24rpx;
  border: 1rpx solid rgba(168, 230, 207, 0.15);
}

.trip-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.trip-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--text-body);
}

.trip-arrow {
  flex-shrink: 0;
}

.trip-meta {
  font-size: 26rpx;
  color: var(--text-secondary);
  margin-top: 12rpx;
  display: block;
}

.trip-date {
  font-size: 24rpx;
  color: $mint-primary;
  margin-top: 8rpx;
  display: block;
}

.trip-chat-btn {
  display: inline-flex;
  margin-top: 18rpx;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(20, 184, 166, 0.12);
  color: $mint-primary;
  font-size: 24rpx;
}
</style>
