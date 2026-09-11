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

    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
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

const keyword = ref('')
const category = ref('全部')
const city = ref('')
const list = ref<ScenicItem[]>([])
const loading = ref(false)
const page = ref(1)
const noMore = ref(false)

onMounted(() => {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1] as { options?: { city?: string } }
  city.value = current.options?.city || ''
  search()
})

async function search() {
  page.value = 1
  noMore.value = false
  loading.value = true
  const cat = category.value === '全部' ? undefined : category.value
  const res = await withFallback(
    () => getScenicList({ keyword: keyword.value, city: city.value, category: cat, page: 1, pageSize: 20 }),
    // 接口失败回落空列表（展示空态），不使用任何 mock 数据
    { records: [], total: 0, page: 1, pageSize: 20 }
  )
  list.value = res.records
  loading.value = false
}

async function loadMore() {
  if (noMore.value || loading.value) return
  page.value++
  loading.value = true
  const cat = category.value === '全部' ? undefined : category.value
  const res = await withFallback(
    () => getScenicList({ keyword: keyword.value, city: city.value, category: cat, page: page.value, pageSize: 20 }),
    { records: [], total: 0, page: page.value, pageSize: 20 }
  )
  list.value.push(...res.records)
  if (res.records.length < 20) noMore.value = true
  loading.value = false
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
  font-size: 28rpx;
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
  font-size: 24rpx;
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

.name {
  font-size: 30rpx;
  font-weight: 500;
  color: var(--text-body);
}

.meta {
  display: flex;
  gap: 16rpx;
  margin-top: 8rpx;
}

.rating { font-size: 24rpx; color: $warning-color; }
.city { font-size: 24rpx; color: var(--text-secondary); }

.price {
  font-size: 28rpx;
  color: $error-color;
  margin-top: 8rpx;
  display: block;
}
</style>
