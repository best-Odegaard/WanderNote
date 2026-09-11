<template>
  <view class="page">
    <CustomNavbar :title="title" show-back />

    <scroll-view
      scroll-y
      class="scroll-content"
      :style="{ paddingTop: navHeight + 'px' }"
    >
      <LoadingView v-if="loading && posts.length === 0" />
      <EmptyState v-if="!loading && posts.length === 0" title="暂无内容" />
      <view v-else class="list">
        <CommunityCard
          v-for="item in posts"
          :key="item.id"
          :item="item"
          :show-delete="type === 'mine'"
          @delete="handleDelete"
        />
      </view>

      <view class="safe-bottom" style="height: 40rpx" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import CustomNavbar from '@/components/CustomNavbar/CustomNavbar.vue'
import CommunityCard from '@/components/CommunityCard/CommunityCard.vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import { getMyJournals, getMyCollects, deletePost } from '@/api/community'
import { useLogin } from '@/hooks/useLogin'
import type { CommunityPost } from '@/api/community'

const { checkLogin } = useLogin()

const systemInfo = uni.getSystemInfoSync()
const navHeight = (systemInfo.statusBarHeight || 20) + 44

// 页面参数：type=mine 我的游记 / type=collect 我的收藏
const pages = getCurrentPages()
const page = pages[pages.length - 1] as { options?: { type?: string } }
const type = page.options?.type || 'mine'
const title = type === 'collect' ? '我的收藏' : '我的游记'

const posts = ref<CommunityPost[]>([])
const loading = ref(true)

async function load() {
  if (!checkLogin()) return
  loading.value = true
  try {
    const list = type === 'collect' ? await getMyCollects() : await getMyJournals()
    posts.value = Array.isArray(list) ? list : []
  } catch (e) {
    console.warn('加载列表失败:', e)
    posts.value = []
  } finally {
    loading.value = false
  }
}

// 每次可见时加载（含首次进入、从详情页删除返回后的刷新）
onShow(load)

/** 删除我的游记 */
function handleDelete(item: CommunityPost) {
  uni.showModal({
    title: '删除游记',
    content: '确定删除该游记吗？删除后不可恢复',
    confirmText: '删除',
    confirmColor: '#e64340',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deletePost(item.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        posts.value = posts.value.filter((p) => p.id !== item.id)
        // 同步通知社区列表页刷新
        uni.$emit('journal:deleted', item.id)
      } catch {
        uni.showToast({ title: '删除失败，请重试', icon: 'none' })
      }
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
  padding: 0 24rpx;
  box-sizing: border-box;
}

.list {
  padding: 16rpx 0;
}
</style>
