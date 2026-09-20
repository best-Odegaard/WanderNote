<template>
  <view class="page">
    <CustomNavbar title="社区">
      <template #right>
        <text class="publish-btn" @tap="goPublish">发布</text>
      </template>
    </CustomNavbar>

    <scroll-view
      scroll-y
      class="scroll-content"
      :style="{ paddingTop: navHeight + 'px' }"
      @scrolltolower="loadMore"
    >
      <!-- 瀑布流布局 -->
      <view class="waterfall">
        <view class="column">
          <CommunityCard v-for="item in leftColumn" :key="item.id" :item="item" />
        </view>
        <view class="column">
          <CommunityCard v-for="item in rightColumn" :key="item.id" :item="item" />
        </view>
      </view>

      <LoadingView v-if="loading && posts.length === 0" />
      <EmptyState v-if="!loading && posts.length === 0" title="暂无游记" button-text="发布第一篇" @action="goPublish" />
      <view v-if="loading && posts.length > 0" class="load-more">加载中...</view>
      <view v-if="noMore && posts.length > 0" class="load-more">— 没有更多了 —</view>

      <view class="safe-bottom" style="height: 40rpx" />
    </scroll-view>

    <!-- 悬浮发布按钮 -->
    <view class="fab" @tap="goPublish">
      <text class="fab-icon">+</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import CustomNavbar from '@/components/CustomNavbar/CustomNavbar.vue'
import CommunityCard from '@/components/CommunityCard/CommunityCard.vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import { getCommunityList } from '@/api/community'
import { withFallback } from '@/utils/mock'
import { useLogin } from '@/hooks/useLogin'
import type { CommunityPost } from '@/api/community'

const { checkLogin } = useLogin()

const systemInfo = uni.getSystemInfoSync()
const navHeight = (systemInfo.statusBarHeight || 20) + 44

const posts = ref<CommunityPost[]>([])
const loading = ref(false)
const page = ref(1)
const noMore = ref(false)
/** 上次发布时的回调标记，用于发布后自动刷新 */
let pendingRefresh = false

const leftColumn = computed(() => posts.value.filter((_, i) => i % 2 === 0))
const rightColumn = computed(() => posts.value.filter((_, i) => i % 2 === 1))

async function loadPosts(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 1
    noMore.value = false
  }
  loading.value = true

  const res = await withFallback(
    () => getCommunityList({ pageNum: page.value, pageSize: 10 }),
    // 接口失败回落空列表（展示空态），不使用任何 mock 数据
    { records: [], total: 0 }
  )

  if (reset) {
    posts.value = res.records
  } else {
    posts.value.push(...res.records)
  }

  if (res.records.length < 10) noMore.value = true
  loading.value = false
}

function loadMore() {
  if (noMore.value || loading.value) return
  page.value++
  loadPosts()
}

function goPublish() {
  if (!checkLogin()) return
  // 标记：从发布页返回后需要刷新列表
  pendingRefresh = true
  uni.navigateTo({ url: '/pages/community/publish' })
}

/** 首次加载 */
onMounted(() => {
  loadPosts(true)
  // 详情页/我的游记删除后，返回时自动刷新列表
  uni.$on('journal:deleted', () => {
    pendingRefresh = true
  })
})

onUnmounted(() => {
  uni.$off('journal:deleted')
})

/** 每次页面可见时重新加载（兼容从发布页返回） */
onShow(() => {
  if (pendingRefresh) {
    pendingRefresh = false
    loadPosts(true)
  }
})
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
}

.scroll-content {
  height: 100vh;
  padding: 0 16rpx;
  box-sizing: border-box;
}

.publish-btn {
  font-size: var(--fs-body);
  color: $primary-color;
}

.waterfall {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 0;
}

.column {
  flex: 1;
  min-width: 0;
}

.load-more {
  text-align: center;
  padding: 24rpx;
  font-size: var(--fs-meta);
  color: var(--text-placeholder);
}

.fab {
  position: fixed;
  right: 40rpx;
  bottom: 160rpx;
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, $primary-color, #2563EB);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba($primary-color, 0.4);
  z-index: 100;
}

.fab-icon {
  font-size: 52rpx;
  color: #fff;
  font-weight: 300;
}
</style>
