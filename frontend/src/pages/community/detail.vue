<template>
  <view class="page">
    <LoadingView v-if="loading" />
    <!-- 空状态：游记获取失败或不存在时不兜底假数据 -->
    <view v-else-if="!post" class="empty-state">
      <text class="empty-emoji">📝</text>
      <text class="empty-text">游记获取失败或已删除</text>
    </view>
    <view v-else-if="post">
      <swiper v-if="post.images?.length" class="banner" circular>
        <swiper-item v-for="(img, i) in post.images" :key="i">
          <image :src="img" mode="aspectFill" class="banner-img" @tap="previewImage(i)" />
        </swiper-item>
      </swiper>
      <image v-else class="cover" :src="post.cover" mode="aspectFill" @tap="previewImage(0)" />

      <view class="content">
        <text class="title">{{ post.title }}</text>
        <!-- 游记发布城市 -->
        <view v-if="post.location" class="location-chip">
          <text class="location-pin">📍</text>
          <text class="location-text">{{ post.location }}</text>
        </view>
        <view class="author" @tap="followAuthor">
          <image class="avatar" :src="post.author.avatar" mode="aspectFill" />
          <text class="nickname">{{ post.author.nickname }}</text>
          <text v-if="isMine" class="delete-btn" @tap.stop="handleDelete">删除</text>
          <text v-else-if="!post.isFollowed" class="follow-btn">+ 关注</text>
        </view>
        <text class="body">{{ post.content || '精彩内容...' }}</text>
        <view v-if="post.tags?.length" class="tags">
          <text v-for="tag in post.tags" :key="tag" class="tag">#{{ tag }}</text>
        </view>
      </view>

      <view class="action-bar safe-bottom">
        <view class="action-group">
          <view class="action" @tap="toggleLike">
            <text>{{ post.isLiked ? '❤️' : '🤍' }}</text>
            <text>{{ formatCount(post.likeCount) }}</text>
          </view>
          <view class="action" @tap="toggleCollect">
            <text>{{ post.isCollected ? '⭐' : '☆' }}</text>
            <text>{{ formatCount(post.collectCount) }}</text>
          </view>
          <view class="action" @tap="share">
            <text>📤</text>
            <text>分享</text>
          </view>
        </view>
        <!-- 浏览者：一键用游记城市创建行程 -->
        <view v-if="canCreateTrip" class="create-trip-btn" @tap="goCreateTrip">
          <text class="create-trip-text">＋ 创建行程</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import { getCommunityDetail, deletePost, toggleJournalLike, collectPost, uncollectPost, followAuthor as followApi } from '@/api/community'
import { formatCount } from '@/utils/format'
import { useUserStore } from '@/store/user'
import { useTripStore } from '@/store/trip'
import { useLogin } from '@/hooks/useLogin'
import type { CommunityPost } from '@/api/community'

const loading = ref(true)
const post = ref<CommunityPost | null>(null)
const userStore = useUserStore()
const tripStore = useTripStore()
const { checkLogin } = useLogin()

/** 当前登录用户是否为游记发布者（仅发布者可删除） */
const isMine = computed(() => {
  if (!post.value) return false
  const currentId = userStore.userInfo?.id
  return !!currentId && (post.value.userId === currentId || post.value.author.id === currentId)
})

/** 浏览者（非作者本人）且游记带发布城市时，展示"创建行程"按钮 */
const canCreateTrip = computed(() => {
  return !!post.value && !isMine.value && !!post.value.location
})

/** 点击"创建行程"：跳转到智能规划向导页，自动填入游记城市，
 *  并把游记内容（标题/标签/摘要）作为行程上下文带入 AI 第一轮对话 */
function goCreateTrip() {
  if (!post.value) return
  if (!checkLogin()) return
  const city = post.value.location?.trim() || ''
  if (!city) return

  // 组装游记上下文：让 AI 第一轮就知道用户参考了这篇游记
  const ctxParts: string[] = []
  if (post.value.title) ctxParts.push(`参考游记：《${post.value.title}》`)
  if (post.value.tags?.length) ctxParts.push(`游记主题：${post.value.tags.join('、')}`)
  if (post.value.content) ctxParts.push(`游记摘要：${post.value.content.replace(/\s+/g, ' ').slice(0, 100)}`)
  tripStore.pendingTripContext = ctxParts.join('；')

  uni.navigateTo({ url: `/pages/plan/wizard?city=${encodeURIComponent(city)}` })
}

onMounted(async () => {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as { options?: { id?: string } }
  const id = Number(page.options?.id || 0)
  try {
    post.value = await getCommunityDetail(id)
  } catch (e) {
    console.warn('[community/detail] 获取游记详情失败:', e)
    // 不兜底 mock 数据，展示空状态
  }
  loading.value = false
})

async function toggleLike() {
  if (!post.value) return
  const wasLiked = post.value.isLiked
  // 乐观更新 UI
  post.value.isLiked = !wasLiked
  post.value.likeCount += post.value.isLiked ? 1 : -1
  try {
    await toggleJournalLike(post.value.id)
    uni.showToast({ title: post.value.isLiked ? '点赞成功' : '已取消点赞', icon: 'none' })
  } catch {
    // 请求失败，回滚 UI
    post.value.isLiked = wasLiked
    post.value.likeCount += wasLiked ? 1 : -1
    uni.showToast({ title: '操作失败，请重试', icon: 'none' })
  }
}

async function toggleCollect() {
  if (!post.value) return
  const wasCollected = post.value.isCollected
  // 乐观更新 UI
  post.value.isCollected = !wasCollected
  post.value.collectCount += post.value.isCollected ? 1 : -1
  try {
    if (post.value.isCollected) {
      await collectPost(post.value.id)
    } else {
      await uncollectPost(post.value.id)
    }
    uni.showToast({ title: post.value.isCollected ? '收藏成功' : '已取消收藏', icon: 'none' })
  } catch {
    // 请求失败，回滚 UI
    post.value.isCollected = wasCollected
    post.value.collectCount += wasCollected ? 1 : -1
    uni.showToast({ title: '操作失败，请重试', icon: 'none' })
  }
}

function followAuthor() {
  if (!post.value) return
  post.value.isFollowed = true
  uni.showToast({ title: '关注成功', icon: 'none' })
}

function share() {
  uni.showToast({ title: '点击右上角分享', icon: 'none' })
}

/** 删除游记（仅发布者可操作） */
function handleDelete() {
  if (!post.value) return
  uni.showModal({
    title: '删除游记',
    content: '确定删除该游记吗？删除后不可恢复',
    confirmText: '删除',
    confirmColor: '#e64340',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deletePost(post.value!.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        // 通知列表页刷新
        uni.$emit('journal:deleted', post.value!.id)
        setTimeout(() => {
          const pages = getCurrentPages()
          if (pages.length > 1) uni.navigateBack()
          else uni.switchTab({ url: '/pages/community/index' })
        }, 500)
      } catch {
        uni.showToast({ title: '删除失败，请重试', icon: 'none' })
      }
    }
  })
}

/** 点击图片放大预览 */
function previewImage(index: number) {
  if (!post.value) return
  const urls = post.value.images?.length ? post.value.images : post.value.cover ? [post.value.cover] : []
  if (!urls.length) return
  uni.previewImage({
    urls,
    current: urls[index] || urls[0]
  })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

/* 空状态：游记获取失败时的提示 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 200rpx 48rpx;
}

.empty-emoji {
  font-size: 96rpx;
}

.empty-text {
  font-size: var(--fs-body);
  color: var(--text-body);
}

.banner, .cover { width: 100%; height: 500rpx; }
.banner-img { width: 100%; height: 100%; }

.content { padding: 32rpx; padding-bottom: 140rpx; }

.title {
  font-size: var(--fs-subhead);
  font-weight: 700;
  color: var(--text-main);
  display: block;
  line-height: 1.4;
}

.author {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 24rpx 0;
}

.avatar { width: 72rpx; height: 72rpx; border-radius: 50%; }
.nickname { flex: 1; font-size: var(--fs-body); font-weight: 600; color: var(--text-main); }

.follow-btn {
  font-size: var(--fs-body);
  font-weight: 600;
  color: $primary-color;
  border: 2rpx solid $primary-color;
  padding: 8rpx 24rpx;
  border-radius: 28rpx;
}

.delete-btn {
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--danger);
  border: 2rpx solid var(--danger);
  padding: 8rpx 24rpx;
  border-radius: 28rpx;
}

.body {
  font-size: var(--fs-body);
  color: var(--text-main);
  line-height: 1.9;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 28rpx;
}

.tag {
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--tag-text);
  background: linear-gradient(135deg, var(--tag-a), var(--tag-b));
  border: 2rpx solid rgba(72, 187, 136, 0.3);
  padding: 10rpx 26rpx;
  border-radius: 9999rpx;
}

.location-chip {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  margin-top: 16rpx;
  padding: 8rpx 24rpx;
  background: var(--bg-input);
  border-radius: 999rpx;
}

.location-pin {
  font-size: var(--fs-body);
}

.location-text {
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--text-secondary);
}

.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  background: var(--bg-card);
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
}

.action-group {
  display: flex;
  justify-content: space-around;
  flex: 1;
  min-width: 0;
}

.action {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: var(--fs-body);
  font-weight: 500;
  color: var(--text-main);
  gap: 4rpx;
}

.create-trip-btn {
  flex-shrink: 0;
  margin-left: 24rpx;
  padding: 18rpx 36rpx;
  background: linear-gradient(135deg, #48bb88, #2f9d6f);
  border-radius: 999rpx;
  box-shadow: 0 6rpx 20rpx rgba(47, 157, 111, 0.35);
}

.create-trip-text {
  color: #fff;
  font-size: var(--fs-body);
  font-weight: 600;
}
</style>
