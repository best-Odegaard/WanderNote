<template>
  <view class="page">
    <view class="search-bar">
      <input v-model="keyword" class="search-input" placeholder="搜索景点" confirm-type="search" @confirm="search" />
    </view>

    <view class="filters">
      <scroll-view scroll-x class="filter-scroll">
        <text
          v-for="cat in SCENIC_CATEGORIES"
          :key="cat"
          class="filter-tag"
          :class="{ active: category === cat }"
          @tap="category = cat; search()"
        >{{ cat }}</text>
      </scroll-view>
    </view>

    <scroll-view
      scroll-y
      class="list"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view v-for="item in list" :key="item.id" class="list-item card" @tap="goDetail(item.id)">
        <image class="cover" :src="item.cover" mode="aspectFill" />
        <view class="info">
          <text class="name">{{ item.name }}</text>
          <view class="meta">
            <text class="rating">⭐ {{ formatRating(item.rating) }}</text>
            <text class="city">{{ item.city }}</text>
          </view>
          <text class="price">{{ item.price === 0 ? '免费' : formatPrice(item.price || 0) }}</text>
        </view>
      </view>
      <LoadingView v-if="loading && list.length === 0" />
      <EmptyState v-if="!loading && list.length === 0" />
      <!-- 与社区/探索列表对齐的加载态与「没有更多」提示 -->
      <view v-if="loading && list.length > 0" class="load-more">加载中...</view>
      <view v-if="noMore && list.length > 0" class="load-more">— 没有更多了 —</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import { SCENIC_CATEGORIES } from '@/utils/constant'
import { getScenicList } from '@/api/scenic'
import { formatRating, formatPrice } from '@/utils/format'
import { withFallback } from '@/utils/mock'
import type { ScenicItem } from '@/api/scenic'

const PAGE_SIZE = 20

const keyword = ref('')
const category = ref('全部')
const city = ref('')
const list = ref<ScenicItem[]>([])
const loading = ref(false)
/** 下一页要请求的页码；只有成功拿到满页才推进（页码推进交给成功分支，见 fetchPage） */
const page = ref(1)
const noMore = ref(false)
const refreshing = ref(false)

onMounted(() => {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1] as { options?: { city?: string } }
  city.value = current.options?.city || ''
  search()
})

/**
 * 统一的分页拉取：reset=true 表示首屏/筛选/下拉刷新（替换列表），否则为追加。
 *
 * 页码推进放在这里而不是调用点：原来 loadMore 里先 `page++` 再请求，
 * 一旦 loading 守卫把这次请求挡掉，页码已经加过 —— 那一页数据被永久跳过（P2-15）。
 * 现在只有「真的拿到满页」才推进页码。
 */
async function fetchPage(targetPage: number, reset: boolean) {
  const cat = category.value === '全部' ? undefined : category.value
  loading.value = true
  const res = await withFallback(
    () => getScenicList({ keyword: keyword.value, city: city.value, category: cat, page: targetPage, pageSize: PAGE_SIZE }),
    // 接口失败回落空列表（展示空态），不使用任何 mock 数据
    { records: [], total: 0, page: targetPage, pageSize: PAGE_SIZE }
  )
  if (reset) list.value = res.records
  else list.value.push(...res.records)
  if (res.records.length < PAGE_SIZE) {
    // 首屏不足一页就是「到底了」：原来首屏不设 noMore，滚到底会反复请求空页
    noMore.value = true
  } else {
    page.value = targetPage + 1
  }
  loading.value = false
}

async function search() {
  page.value = 1
  noMore.value = false
  await fetchPage(1, true)
}

async function loadMore() {
  if (noMore.value || loading.value) return
  await fetchPage(page.value, false)
}

/** 下拉刷新：回到第一页重新拉（筛选条件保留） */
async function onRefresh() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    page.value = 1
    noMore.value = false
    await fetchPage(1, true)
  } finally {
    refreshing.value = false
  }
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/scenic/detail?id=${id}` })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
  display: flex;
  flex-direction: column;
}

.search-bar {
  padding: 20rpx 32rpx;
  background: var(--bg-card);
}

.search-input {
  height: 72rpx;
  padding: 0 24rpx;
  background: var(--bg-input);
  border-radius: 36rpx;
  font-size: var(--fs-body);
}

.filters {
  padding: 16rpx 32rpx;
  background: var(--bg-card);
}

.filter-scroll { white-space: nowrap; }

.filter-tag {
  display: inline-block;
  padding: 8rpx 20rpx;
  margin-right: 12rpx;
  font-size: var(--fs-meta);
  color: var(--text-secondary);
  background: var(--bg-input);
  border-radius: 24rpx;

  &.active {
    background: rgba($primary-color, 0.1);
    color: $primary-color;
  }
}

.list {
  flex: 1;
  padding: 24rpx 32rpx;
  height: calc(100vh - 200rpx);
}

.list-item {
  display: flex;
  gap: 20rpx;
  margin-bottom: 20rpx;
}

.cover {
  width: 200rpx;
  height: 150rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
}

.info { flex: 1; }

.load-more {
  text-align: center;
  padding: 24rpx;
  font-size: var(--fs-meta);
  color: var(--text-placeholder);
}

.name {
  font-size: var(--fs-title);
  font-weight: 500;
  color: var(--text-body);
}

.meta {
  display: flex;
  gap: 16rpx;
  margin-top: 8rpx;
}

.rating { font-size: var(--fs-meta); color: $warning-color; }
.city { font-size: var(--fs-meta); color: var(--text-secondary); }

.price {
  font-size: var(--fs-body);
  color: $error-color;
  margin-top: 8rpx;
  display: block;
}
</style>
