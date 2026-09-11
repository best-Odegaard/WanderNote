<template>
  <view class="page page-with-tabbar">
    <CustomNavbar title="我的" />

    <scroll-view scroll-y class="scroll-content" :style="{ paddingTop: navHeight + 'px' }">
      <!-- 用户信息 -->
      <view class="user-card" @tap="handleUserTap">
        <image class="avatar" :src="userInfo?.avatar || defaultAvatar" mode="aspectFill" />
        <view class="user-info">
          <text class="nickname">{{ userInfo?.nickname || '点击登录' }}</text>
          <text class="bio">{{ userInfo?.bio || '探索世界，记录美好' }}</text>
        </view>
        <text class="arrow" @tap.stop>
          <AppIcon name="chevron-right" :size="30" color="var(--text-tertiary)" />
        </text>
      </view>

      <!-- 数据统计 -->
      <view class="stats card">
        <view class="stat-item" @tap="goTripTab">
          <text class="stat-num">{{ tripCount }}</text>
          <text class="stat-label">行程</text>
        </view>
        <view class="stat-item" @tap="goJournals('collect')">
          <text class="stat-num">{{ collectCount }}</text>
          <text class="stat-label">收藏</text>
        </view>
        <view class="stat-item" @tap="goJournals('mine')">
          <text class="stat-num">{{ journalCount }}</text>
          <text class="stat-label">游记</text>
        </view>
        <view class="stat-item">
          <text class="stat-num">0</text>
          <text class="stat-label">关注</text>
        </view>
      </view>

      <!-- 功能菜单 -->
      <view class="menu card">
        <view v-for="item in menuItems" :key="item.label" class="menu-item" @tap="goPage(item.path)">
          <AppIcon :name="item.icon" :size="34" :color="item.color" class="menu-icon" />
          <text class="menu-label">{{ item.label }}</text>
          <AppIcon name="chevron-right" :size="28" color="var(--text-tertiary)" class="menu-arrow" />
        </view>
      </view>

      <button v-if="isLogin" class="logout-btn" @tap="handleLogout">退出登录</button>

      <view class="safe-bottom" style="height: 40rpx" />
    </scroll-view>

    <!-- #ifndef MP-WEIXIN -->
    <AppTabBar />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/store/app'
import AppTabBar from '@/components/AppTabBar/AppTabBar.vue'
import { useTabBarPage } from '@/hooks/useTabBarPage'
import CustomNavbar from '@/components/CustomNavbar/CustomNavbar.vue'
import { useUserStore } from '@/store/user'
import { useTripStore } from '@/store/trip'
import { getMyCollects, getMyJournals } from '@/api/community'
import { isLoggedIn } from '@/utils/auth'

const appStore = useAppStore()
const userStore = useUserStore()
const tripStore = useTripStore()
const { userInfo } = storeToRefs(userStore)

const systemInfo = uni.getSystemInfoSync()
const navHeight = (systemInfo.statusBarHeight || 20) + 44
const defaultAvatar = 'https://picsum.photos/seed/default/200/200'
const tripCount = ref(0)
const collectCount = ref(0)
const journalCount = ref(0)

const isLogin = computed(() => isLoggedIn())

const menuItems = [
  { icon: 'star', color: '#f5b942', label: '我的收藏', path: '/pages/profile/journals?type=collect' },
  { icon: 'edit', color: '#58a883', label: '我的游记', path: '/pages/profile/journals?type=mine' },
  { icon: 'map', color: '#4a9ef5', label: '我的行程', path: '/pages/trip/index' },
  { icon: 'eye', color: '#9b7bd8', label: '浏览历史', path: '' },
  { icon: 'message', color: '#4a9ef5', label: '意见反馈', path: '/pages/profile/feedback' },
  { icon: 'info', color: '#f59e0b', label: '关于我们', path: '' },
  { icon: 'settings', color: '#94a3b8', label: '设置中心', path: '/pages/profile/setting' }
]

useTabBarPage(3)

onMounted(async () => {
  if (isLogin.value) {
    try {
      const list = await tripStore.loadHistory()
      tripCount.value = list.length
    } catch {
      tripCount.value = 0
    }
    // 收藏数 / 游记数
    try {
      const [collects, journals] = await Promise.all([getMyCollects(), getMyJournals()])
      collectCount.value = (collects || []).length
      journalCount.value = (journals || []).length
    } catch {
      collectCount.value = 0
      journalCount.value = 0
    }
  }
})

function handleUserTap() {
  if (isLogin.value) {
    uni.navigateTo({ url: '/pages/profile/edit' })
  } else {
    uni.navigateTo({ url: '/pages/auth/login' })
  }
}

function goTripTab() {
  appStore.setTabbarIndex(1)
  uni.switchTab({ url: '/pages/trip/index' })
}

function goJournals(type: string) {
  if (!isLogin.value) {
    uni.navigateTo({ url: '/pages/auth/login' })
    return
  }
  uni.navigateTo({ url: `/pages/profile/journals?type=${type}` })
}

const tabPaths = ['/pages/home/index', '/pages/trip/index', '/pages/coming/index', '/pages/profile/index']

function goPage(path: string) {
  if (!path) {
    uni.showToast({ title: '功能开发中', icon: 'none' })
    return
  }
  if (path.includes('trip') && !isLogin.value) {
    uni.navigateTo({ url: '/pages/auth/login' })
    return
  }
  const tabIdx = tabPaths.indexOf(path)
  if (tabIdx >= 0) {
    appStore.setTabbarIndex(tabIdx)
    uni.switchTab({ url: path })
    return
  }
  uni.navigateTo({ url: path })
}

function handleLogout() {
  uni.showModal({
    title: '提示',
    content: '确定退出登录？',
    success: (res) => {
      if (res.confirm) userStore.logout()
    }
  })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
}

.scroll-content {
  height: 100vh;
  padding: 0 32rpx;
  box-sizing: border-box;
}

.user-card {
  display: flex;
  align-items: center;
  padding: 40rpx 0;
  gap: 24rpx;
}

.avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  border: 4rpx solid var(--bg-card);
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.1);
}

.user-info {
  flex: 1;
}

.nickname {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--text-main);
  display: block;
}

.bio {
  font-size: 26rpx;
  color: var(--text-secondary);
  margin-top: 8rpx;
  display: block;
}

.arrow {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.stats {
  display: flex;
  margin-bottom: 24rpx;
}

.stat-item {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
}

.stat-num {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--text-main);
  display: block;
}

.stat-label {
  font-size: 24rpx;
  color: var(--text-secondary);
  margin-top: 4rpx;
  display: block;
}

.menu {
  margin-bottom: 32rpx;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 28rpx 0;
  border-bottom: 1rpx solid var(--border);

  &:last-child { border-bottom: none; }
}

.menu-icon {
  margin-right: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  border-radius: 14rpx;
  background: var(--bg-input);
}

.menu-label {
  flex: 1;
  font-size: 30rpx;
  color: var(--text-main);
}

.menu-arrow {
  display: flex;
  align-items: center;
}

.logout-btn {
  background: var(--bg-card);
  color: $error-color;
  font-size: 30rpx;
  border-radius: $card-radius;
  height: 88rpx;
  line-height: 88rpx;
  border: none;

  &::after { border: none; }
}
</style>
