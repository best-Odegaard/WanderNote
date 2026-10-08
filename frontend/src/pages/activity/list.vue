<template>
  <view class="page">
    <scroll-view
      scroll-y
      class="list"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <ActivityCard v-for="item in list" :key="item.id" :item="item" />
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
import ActivityCard from '@/components/ActivityCard/ActivityCard.vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import { getActivityList } from '@/api/activity'
import { withFallback } from '@/utils/mock'
import type { ActivityItem } from '@/api/activity'

const PAGE_SIZE = 20

const list = ref<ActivityItem[]>([])
const loading = ref(false)
/** 下一页要请求的页码；只有成功拿到满页才推进 */
const page = ref(1)
const noMore = ref(false)
const refreshing = ref(false)

onMounted(() => loadData(true))

async function loadData(reset = false) {
  if (loading.value) return
  if (reset) { page.value = 1; noMore.value = false }
  loading.value = true

  const targetPage = page.value
  const res = await withFallback(
    () => getActivityList({ page: targetPage, pageSize: PAGE_SIZE }),
    // 接口失败回落空列表（展示空态），不使用任何 mock 数据
    { records: [], total: 0 }
  )
  if (reset) list.value = res.records
  else list.value.push(...res.records)
  if (res.records.length < PAGE_SIZE) {
    noMore.value = true
  } else {
    // 页码只在「拿到满页 → 后面还有数据」时推进。
    // 原来在 loadMore 里先 `page++` 再调 loadData()，而 loadData 开头有 loading 守卫，
    // 上拉触发时若正有请求在跑就会直接 return —— 但页码已经加过了，
    // 这一次的页面数据被永久跳过（P2-15）。
    page.value = targetPage + 1
  }
  loading.value = false
}

function loadMore() {
  if (noMore.value || loading.value) return
  loadData()
}

/** 下拉刷新：重置到第一页重新拉（失败时 withFallback 会回落到空列表 + 空态） */
async function onRefresh() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    await loadData(true)
  } finally {
    refreshing.value = false
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
}

.list {
  padding: 24rpx 32rpx;
  height: 100vh;
}

.load-more {
  text-align: center;
  padding: 24rpx;
  font-size: var(--fs-meta);
  color: var(--text-placeholder);
}
</style>
