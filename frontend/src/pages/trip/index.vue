<template>
  <view class="page page-with-tabbar">
    <view class="header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <text class="title">我的行程</text>
    </view>

    <scroll-view scroll-y class="scroll-body" :style="{ paddingTop: headerHeight + 'px' }">
      <LoadingView v-if="loading" />

      <!--
        未登录：明确说清「我的行程要登录」，并给一个去登录的按钮。
        以前这里什么判断都没有：匿名进来看空态写着「还没有行程，点这里填问卷」，
        用户填完问卷才在最后一步被弹去登录（P1-02 的另一半）。
      -->
      <view v-else-if="!isLogin" class="empty soft-shadow" @tap="goLogin">
        <AppIcon name="user" :size="96" color="var(--text-tertiary)" />
        <text class="empty-title">登录后查看我的行程</text>
        <text class="empty-hint">点这里登录，行程会自动同步到账号里</text>
      </view>

      <view v-else-if="cards.length === 0" class="empty soft-shadow" @tap="goCreate">
        <AppIcon name="calendar" :size="96" color="var(--text-tertiary)" />
        <text class="empty-title">还没有行程</text>
        <text class="empty-hint">点这里填个问卷，小笺就能开始规划</text>
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

    <!--
      右下角悬浮「+」：新建行程（设计稿位置）。
      自定义 TabBar 中间那颗创建按钮已去掉，新建行程统一从这里进 ——
      点一下直接到问卷页（pages/plan/survey），比先弹一层菜单少一步。
      放在本页而不是首页：首页是全屏对话，底部被输入框和「生成行程计划」占满，
      悬浮按钮只能压在消息上；这里是「我的行程」，新建按钮落在这里也更顺。
    -->
    <view class="fab" @tap="goCreate">
      <AppIcon name="plus" :size="52" color="var(--on-brand)" :stroke-width="2.6" />
    </view>

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
import { ref, computed } from 'vue'
import { storeToRefs } from 'pinia'
import MyPlanCard from '@/components/MyPlanCard/MyPlanCard.vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import AppIcon from '@/components/AppIcon/AppIcon.vue'
import AppTabBar from '@/components/AppTabBar/AppTabBar.vue'
import { useTabBarPage } from '@/hooks/useTabBarPage'
import { useTripStore } from '@/store/trip'
import { useUserStore } from '@/store/user'
import { redirectToLogin } from '@/utils/auth'
import { toMyPlanItem } from '@/utils/tripCard'
import type { TripPlan } from '@/api/trip'

const tripStore = useTripStore()
// 用 store 里的响应式登录态：登录/退出后本页无需重进就能切换空态
const { isLogin } = storeToRefs(useUserStore())

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const headerHeight = statusBarHeight + 88

const trips = ref<TripPlan[]>([])
const loading = ref(true)

/** 原始行程 + 卡片视图模型配对，避免在模板里反复做转换 */
const cards = computed(() =>
  trips.value.map((trip, i) => ({ trip, item: toMyPlanItem(trip, i) }))
)

// 只在 onShow 里加载（useTabBarPage 内部就是 onShow）：
// 之前这里还额外挂了 onMounted(() => loadTrips())，首次进页会并发两次 /trip/list ——
// 令牌过期时表现为连弹两次「请先登录」+ 两个登录页叠栈。
useTabBarPage(1, loadTrips)

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

/** 新建行程：走问卷 → 选酒店 → 对话这条主链路（与首页悬浮「+」同一个入口） */
function goCreate() {
  uni.navigateTo({ url: '/pages/plan/survey' })
}

/** 未登录空态的入口：带 redirect 回本页（行程 tab 不能作为回跳目标，用首页兜底） */
function goLogin() {
  redirectToLogin()
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

/* 右下角悬浮「+ 新建行程」：抬起一个 TabBar 的高度（约 160rpx），避免被底栏压住 */
.fab {
  position: fixed;
  right: 32rpx;
  bottom: calc(200rpx + env(safe-area-inset-bottom));
  z-index: 900;
  width: 104rpx;
  height: 104rpx;
  border-radius: 50%;
  background: var(--brand-grad);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rpx solid var(--glass-border);
  box-shadow: var(--brand-glow), var(--shadow-md);
  transition: transform 0.2s ease;

  &:active {
    transform: scale(0.92);
  }
}
</style>
