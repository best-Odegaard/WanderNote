<template>
  <view class="page">
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <ActivityCard v-for="item in list" :key="item.id" :item="item" />
      <LoadingView v-if="loading && list.length === 0" />
      <EmptyState v-if="!loading && list.length === 0" />
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

const list = ref<ActivityItem[]>([])
const loading = ref(false)
const page = ref(1)
const noMore = ref(false)

onMounted(() => loadData(true))

async function loadData(reset = false) {
  if (loading.value) return
  if (reset) { page.value = 1; noMore.value = false }
  loading.value = true
  const res = await withFallback(
    () => getActivityList({ page: page.value, pageSize: 20 }),
    // 接口失败回落空列表（展示空态），不使用任何 mock 数据
    { records: [], total: 0 }
  )
  if (reset) list.value = res.records
  else list.value.push(...res.records)
  if (res.records.length < 20) noMore.value = true
  loading.value = false
}

function loadMore() {
  if (noMore.value) return
  page.value++
  loadData()
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
</style>
