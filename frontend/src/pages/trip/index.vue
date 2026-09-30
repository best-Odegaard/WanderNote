<template>
  <view class="page page-with-tabbar">
    <view class="header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <text class="title">我的行程</text>
    </view>

    <scroll-view scroll-y class="scroll-body" :style="{ paddingTop: headerHeight + 'px' }">
      <LoadingView v-if="loading" />

      <view v-else-if="cards.length === 0" class="empty soft-shadow" @tap="goCreate">
        <AppIcon name="calendar" :size="96" color="var(--text-tertiary)" />
        <text class="empty-title">还没有行程</text>
        <text class="empty-hint">去首页和 AI 聊一句，就能生成行程</text>
      </view>

      <view v-else class="trip-list">
        <MyPlanCard
          v-for="(c, i) in cards"
          :key="String(c.trip.id)"
          :plan="c.item"
          :index="i"
          :show-chat-entry="!!c.trip.chatSessionId"
          @tap="viewTrip(c.trip)"
          @chat="openTripChat(c.trip)"
        />
      </view>

      <view style="height: 160rpx" />
    </scroll-view>

    <!-- #ifndef MP-WEIXIN -->
    <AppTabBar />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
/**
 * 我的行程。
 *
 * 卡片样式来自原首页的 MyPlanCard（带状态徽章、日期区间、地点数、封面缩略图），
 * 首页改成对话界面后，这套卡片整体迁到这里；原来行程页自绘的卡片上独有的
 * 「进入行程对话」入口合并进了 MyPlanCard（showChatEntry），迁移不丢能力。
 */
import { ref, computed, onMounted } from 'vue'
import MyPlanCard from '@/components/MyPlanCard/MyPlanCard.vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import AppIcon from '@/components/AppIcon/AppIcon.vue'
import AppTabBar from '@/components/AppTabBar/AppTabBar.vue'
import { useTabBarPage } from '@/hooks/useTabBarPage'
import { useTripStore } from '@/store/trip'
import { toMyPlanItem } from '@/utils/tripCard'
import type { TripPlan } from '@/api/trip'

const tripStore = useTripStore()

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const headerHeight = statusBarHeight + 88

const trips = ref<TripPlan[]>([])
const loading = ref(true)

/** 原始行程 + 卡片视图模型配对，避免在模板里反复做转换 */
const cards = computed(() =>
  trips.value.map((trip, i) => ({ trip, item: toMyPlanItem(trip, i) }))
)

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

/** 新建行程的入口现在是首页的对话：引导用户切到首页说一句 */
function goCreate() {
  uni.switchTab({ url: '/pages/home/index' })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
  position: relative;
}

.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  // 玻璃顶栏：滚动时内容从下方透出，替代原来的实色挡板
  background: var(--glass-bg-strong);
  backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  border-bottom: 1rpx solid var(--glass-border);
  padding: 0 32rpx 24rpx;
}

.title {
  font-size: 46rpx;
  font-weight: 800;
  color: var(--text-main);
  line-height: 92rpx;
  letter-spacing: -0.6rpx;
}

.scroll-body {
  height: 100vh;
  padding: 0 32rpx;
  box-sizing: border-box;
}

.empty {
  margin-top: 80rpx;
  padding: 88rpx 40rpx;
  // 空态也用玻璃卡，和整体语言一致
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  border: 1rpx solid var(--glass-border);
  border-radius: $card-radius-lg;
  box-shadow: var(--glass-shadow);
  text-align: center;

  .app-icon {
    margin-bottom: 8rpx;
  }
}

.empty-title {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--text-main);
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
</style>
