<template>
  <view class="page page-with-tabbar">
    <view class="header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="header-inner">
        <text class="title">探索</text>
        <view class="publish-btn" @tap="goPublish">
          <AppIcon name="edit" :size="26" color="#58a883" />
          <text>发布</text>
        </view>
      </view>
    </view>

    <scroll-view
      scroll-y
      class="scroll-body"
      :style="{ paddingTop: headerHeight + 'px' }"
      @scrolltolower="onScrollToLower"
    >
      <view class="section">
        <text class="section-title">社区发现</text>

        <view v-if="posts.length > 0" class="waterfall">
          <view class="column">
            <CommunityCard v-for="item in leftColumn" :key="item.id" :item="item" />
          </view>
          <view class="column">
            <CommunityCard v-for="item in rightColumn" :key="item.id" :item="item" />
          </view>
        </view>

        <LoadingView v-if="loading && posts.length === 0" />
        <EmptyState
          v-else-if="!loading && posts.length === 0"
          icon="compass"
          title="暂无发现"
          description="把旅途里的好风景分享出来吧"
          button-text="发布第一条"
          @action="goPublish"
        />
        <view v-if="loading && posts.length > 0" class="load-more">加载中...</view>
        <view v-if="noMore && posts.length > 0" class="load-more">— 没有更多了 —</view>
      </view>

      <view style="height: 160rpx" />
    </scroll-view>

    <!-- #ifndef MP-WEIXIN -->
    <AppTabBar />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import AppTabBar from '@/components/AppTabBar/AppTabBar.vue'
import CommunityCard from '@/components/CommunityCard/CommunityCard.vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import { useTabBarPage } from '@/hooks/useTabBarPage'
import { withFallback } from '@/utils/mock'
import { getCommunityList } from '@/api/community'
import { useLogin } from '@/hooks/useLogin'
import type { CommunityPost } from '@/api/community'

const { checkLogin } = useLogin()

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const headerHeight = statusBarHeight + 56

const PAGE_SIZE = 10

const posts = ref<CommunityPost[]>([])
const loading = ref(false)
const refreshing = ref(false)
const page = ref(1)
const noMore = ref(false)

const leftColumn = computed(() => posts.value.filter((_, i) => i % 2 === 0))
const rightColumn = computed(() => posts.value.filter((_, i) => i % 2 === 1))

// Tab 页每次显示都取首屏：首次进入正常加载，已有数据则静默刷新，
// 保证从详情页点赞/收藏返回后列表计数保持一致
useTabBarPage(2, () => {
  if (posts.value.length > 0) {
    refreshSilent()
  } else {
    loadPosts(true)
  }
})

async function loadPosts(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 1
    noMore.value = false
  }
  loading.value = true

  try {
    const res = await withFallback(
      () => getCommunityList({ pageNum: page.value, pageSize: PAGE_SIZE }),
      // 接口失败回落空列表（展示空态），不使用任何 mock 数据
      { records: [] as CommunityPost[], total: 0 }
    )

    if (reset) {
      posts.value = res.records
    } else {
      posts.value.push(...res.records)
    }

    if (res.records.length < PAGE_SIZE) noMore.value = true
  } finally {
    loading.value = false
  }
}

function onScrollToLower() {
  if (noMore.value || loading.value) return
  page.value++
  loadPosts()
}

/** 静默刷新首屏（不显示加载动画，仅更新数据） */
async function refreshSilent() {
  if (refreshing.value || loading.value) return
  refreshing.value = true
  try {
    const res = await withFallback(
      () => getCommunityList({ pageNum: 1, pageSize: PAGE_SIZE }),
      { records: [] as CommunityPost[], total: 0 }
    )
    posts.value = res.records
    page.value = 1
    noMore.value = res.records.length < PAGE_SIZE
  } finally {
    refreshing.value = false
  }
}

function goPublish() {
  if (!checkLogin()) return
  uni.navigateTo({ url: '/pages/community/publish' })
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
  padding-left: $page-padding;
  padding-right: $page-padding;
}

.header-inner {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--text-main);
}

.publish-btn {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  font-size: 26rpx;
  color: $mint-primary;
}

.scroll-body {
  height: 100vh;
  box-sizing: border-box;
}

.section {
  padding: $page-padding;
}

.section-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--text-main);
  display: block;
  margin-bottom: 20rpx;
}

.waterfall {
  display: flex;
  gap: 16rpx;
}

.column {
  flex: 1;
  min-width: 0;
}

.load-more {
  text-align: center;
  padding: 24rpx;
  font-size: 24rpx;
  color: var(--text-placeholder);
}
</style>
