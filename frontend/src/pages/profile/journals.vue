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
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import CustomNavbar from '@/components/CustomNavbar/CustomNavbar.vue'
import CommunityCard from '@/components/CommunityCard/CommunityCard.vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import { getMyJournals, getMyCollects, deletePost } from '@/api/community'
import { useLogin } from '@/hooks/useLogin'
import type { CommunityPost } from '@/api/community'
import { showModal, showToast } from '@/utils/feedback'

const { checkLogin } = useLogin()

const systemInfo = uni.getSystemInfoSync()
const navHeight = (systemInfo.statusBarHeight || 20) + 44

// 页面参数：type=mine 我的游记 / type=collect 我的收藏
// P2-08：原来在 <script setup> 顶层同步读 getCurrentPages().options —— setup 的执行
// 早于页面拿到 query（见 ChatPlanner.vue:422-427 的说明），options 可能还没就绪，
// type 会恒为 'mine'，于是「我的收藏」入口打开的是「我的游记」。
// 改到 onLoad 里取参数，此时 options 一定已就绪。
const type = ref<'mine' | 'collect'>('mine')
const title = computed(() => (type.value === 'collect' ? '我的收藏' : '我的游记'))

onLoad((options?: Record<string, string>) => {
  type.value = options?.type === 'collect' ? 'collect' : 'mine'
})

const posts = ref<CommunityPost[]>([])
const loading = ref(true)

async function load() {
  if (!checkLogin()) return
  loading.value = true
  try {
    const list = type.value === 'collect' ? await getMyCollects() : await getMyJournals()
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
  showModal({
    title: '删除游记',
    content: '确定删除该游记吗？删除后不可恢复',
    confirmText: '删除',
    confirmColor: '#e64340',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deletePost(item.id)
        showToast({ title: '已删除', icon: 'success' })
        posts.value = posts.value.filter((p) => p.id !== item.id)
        // 同步通知社区列表页刷新
        uni.$emit('journal:deleted', item.id)
      } catch {
        showToast({ title: '删除失败，请重试', icon: 'none' })
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
