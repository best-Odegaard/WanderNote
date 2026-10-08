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
        <view class="author">
          <image class="avatar" :src="post.author.avatar" mode="aspectFill" />
          <text class="nickname">{{ post.author.nickname }}</text>
          <text v-if="isMine" class="delete-btn" @tap.stop="handleDelete">删除</text>
          <!--
            「+ 关注」入口已下线（P1-26）：后端没有 follow 接口，点它只能是假成功
            （本地置位 + 提示「关注成功」，作者端却永远看不到这个粉丝）。
            等后端补上 POST /journal/follow/{authorId} 再接回来，不要用本地状态假装关注成功。
          -->
        </view>
        <text class="body">{{ post.content || '精彩内容...' }}</text>
        <view v-if="post.tags?.length" class="tags">
          <text v-for="tag in post.tags" :key="tag" class="tag">#{{ tag }}</text>
        </view>

        <!--
          评论区（P1-27）：后端评论接口（列表/发表/删除）一直是齐的，前端却没有入口。
          这里补最小可用闭环：列表 + 输入框 + 发表，只对本人评论显示删除。
        -->
        <view class="comment-section">
          <text class="comment-title">评论{{ comments.length ? `（${comments.length}）` : '' }}</text>

          <text v-if="commentsLoading" class="comment-tip">评论加载中...</text>
          <text v-else-if="!comments.length" class="comment-tip">还没有评论，来说两句吧</text>

          <view v-for="c in comments" :key="c.id" class="comment-item">
            <image v-if="c.avatar" class="comment-avatar" :src="c.avatar" mode="aspectFill" />
            <view v-else class="comment-avatar comment-avatar-fallback">
              <text>{{ (c.nickname || '游').slice(0, 1) }}</text>
            </view>
            <view class="comment-main">
              <text class="comment-nickname">{{ c.nickname || '旅行者' }}</text>
              <text class="comment-content">{{ c.content }}</text>
              <text v-if="c.parentUsername" class="comment-reply-to">回复 @{{ c.parentUsername }}</text>
            </view>
            <!-- 只对自己发的评论显示删除（后端也会校验，这里避免给出无意义的入口） -->
            <text v-if="isMyComment(c)" class="comment-delete" @tap="handleDeleteComment(c)">删除</text>
          </view>

          <view class="comment-editor">
            <input
              v-model="commentInput"
              class="comment-input"
              :focus="commentFocus"
              placeholder="说点什么..."
              maxlength="500"
              confirm-type="send"
              @confirm="submitComment"
            />
            <view class="comment-submit" :class="{ disabled: commentSubmitting }" @tap="submitComment">
              <text>{{ commentSubmitting ? '发送中' : '发表' }}</text>
            </view>
          </view>
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
          <!-- 评论入口：显示真实条数并跳到下方评论区（原来详情页完全没有评论入口） -->
          <view class="action" @tap="goComments">
            <text>💬</text>
            <text>{{ formatCount(comments.length) }}</text>
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
import {
  getCommunityDetail,
  deletePost,
  toggleJournalLike,
  collectPost,
  uncollectPost,
  getCommentList,
  addComment,
  deleteComment
} from '@/api/community'
import { formatCount } from '@/utils/format'
import { useUserStore } from '@/store/user'
import { useTripStore } from '@/store/trip'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'
import type { CommunityPost, JournalComment } from '@/api/community'
import { showModal, showToast } from '@/utils/feedback'
// 小程序端分享卡片需要；H5/App 没有这个 API，用条件编译避免其它端引入无用依赖
// #ifdef MP-WEIXIN
import { onShareAppMessage } from '@dcloudio/uni-app'
// #endif

const loading = ref(true)
const post = ref<CommunityPost | null>(null)
const userStore = useUserStore()
const tripStore = useTripStore()

// ── 评论（P1-27）──
const comments = ref<JournalComment[]>([])
const commentsLoading = ref(false)
const commentInput = ref('')
const commentSubmitting = ref(false)
const commentFocus = ref(false)

/**
 * 底栏「评论」入口：滚到评论区。
 *
 * 优先用 selector 定位评论区；个别端不支持 selector 定位时兜底把光标落到输入框
 * （浏览器/小程序会把输入框滚进视野），避免点了没有任何反馈。
 */
function goComments() {
  uni.pageScrollTo({
    selector: '.comment-section',
    duration: 250,
    fail: () => {
      commentFocus.value = false
      setTimeout(() => { commentFocus.value = true }, 50)
    }
  })
}

/**
 * 需登录动作的统一守卫。
 *
 * 用 isLoggedIn() + redirectToLogin()（会带上当前页完整路径与 query，登录后能回到这篇游记），
 * 而不是 useLogin().checkLogin()：后者跳登录不带回跳，用户登录完回到上一页还得重新找这篇游记。
 */
function ensureLogin(): boolean {
  if (isLoggedIn()) return true
  showToast({ title: '请先登录', icon: 'none' })
  setTimeout(() => redirectToLogin(), 300)
  return false
}

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
  if (!ensureLogin()) return
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
  // 详情拿到之后才知道 journalId，评论列表只能在这里拉（失败不阻塞正文，只显示空态）
  if (post.value) loadComments()
})

async function toggleLike() {
  if (!post.value) return
  const wasLiked = post.value.isLiked
  // 乐观更新 UI
  post.value.isLiked = !wasLiked
  post.value.likeCount += post.value.isLiked ? 1 : -1
  try {
    await toggleJournalLike(post.value.id)
    showToast({ title: post.value.isLiked ? '点赞成功' : '已取消点赞', icon: 'none' })
  } catch {
    // 请求失败，回滚 UI
    post.value.isLiked = wasLiked
    post.value.likeCount += wasLiked ? 1 : -1
    showToast({ title: '操作失败，请重试', icon: 'none' })
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
    showToast({ title: post.value.isCollected ? '收藏成功' : '已取消收藏', icon: 'none' })
  } catch {
    // 请求失败，回滚 UI
    post.value.isCollected = wasCollected
    post.value.collectCount += wasCollected ? 1 : -1
    showToast({ title: '操作失败，请重试', icon: 'none' })
  }
}

/**
 * 分享（P2-21）。
 *
 * 原来只弹「点击右上角分享」——H5 端根本没有右上角分享菜单（该页也没定义
 * onShareAppMessage），等于一个永远点不动、给不出结果的死入口。
 * 现在按平台给真实能力：
 *   · H5：优先系统分享（移动端浏览器 navigator.share），不支持/被取消则复制当前页链接
 *   · 小程序：靠原生右上角菜单（onShareAppMessage 已实现）
 *   · App：靠系统分享面板（右上角菜单）
 */
async function share() {
  if (!post.value) return

  // #ifdef H5
  const pageUrl = window.location.href
  const nav = navigator as Navigator & {
    share?: (data: { title?: string; text?: string; url?: string }) => Promise<void>
  }
  if (nav.share) {
    try {
      await nav.share({ title: post.value.title, url: pageUrl })
      return
    } catch (e) {
      // 用户取消或浏览器拒绝：退回复制链接，而不是静默什么都不做
      console.warn('[community/detail] 系统分享取消或失败，改复制链接:', e)
    }
  }
  uni.setClipboardData({
    data: pageUrl,
    success: () => showToast({ title: '链接已复制，粘贴给好友即可分享', icon: 'none' }),
    fail: () => showToast({ title: '复制失败，请手动复制地址栏链接', icon: 'none' })
  })
  // #endif

  // #ifndef H5
  showToast({ title: '请点击右上角菜单分享', icon: 'none' })
  // #endif
}

/** 详情页分享卡片（仅小程序端会用到；H5/App 走 share() 的系统能力） */
// #ifdef MP-WEIXIN
onShareAppMessage(() => ({
  title: post.value?.title || 'WanderNote 行笺',
  path: `/pages/community/detail?id=${post.value?.id || ''}`
}))
// #endif

// ── 评论（P1-27）──

/** 拉取评论列表；失败就如实显示空态，不塞假数据 */
async function loadComments() {
  if (!post.value) return
  commentsLoading.value = true
  try {
    comments.value = await getCommentList(post.value.id)
  } catch (e) {
    console.warn('[community/detail] 评论列表获取失败:', e)
    comments.value = []
  } finally {
    commentsLoading.value = false
  }
}

/** 仅本人发的评论显示删除按钮 */
function isMyComment(c: JournalComment): boolean {
  const currentId = userStore.userInfo?.id
  return !!currentId && c.userId === currentId
}

/**
 * 发表评论。
 *
 * 后端 POST /journal/comment 不返回新评论对象（Result.success() 无 data），
 * 所以成功后必须重新拉列表 —— 不要本地 push 一条「看着像服务端返回」的假评论。
 */
async function submitComment() {
  if (!post.value || commentSubmitting.value) return
  if (!ensureLogin()) return
  const content = commentInput.value.trim()
  if (!content) {
    showToast({ title: '请输入评论内容', icon: 'none' })
    return
  }
  if (content.length > 500) {
    // 与后端 CommentDTO @Size(max = 500) 对齐，先拦下来免得白跑一次请求
    showToast({ title: '评论不能超过 500 字', icon: 'none' })
    return
  }

  commentSubmitting.value = true
  try {
    await addComment(post.value.id, content)
    commentInput.value = ''
    showToast({ title: '评论成功', icon: 'success' })
    await loadComments()
  } catch (e) {
    console.warn('[community/detail] 发表评论失败:', e)
    showToast({ title: '评论失败，请重试', icon: 'none' })
  } finally {
    commentSubmitting.value = false
  }
}

/** 删除本人评论 */
function handleDeleteComment(comment: JournalComment) {
  showModal({
    title: '删除评论',
    content: '确定删除这条评论吗？',
    confirmText: '删除',
    confirmColor: '#e64340',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteComment(comment.id)
        showToast({ title: '已删除', icon: 'success' })
        await loadComments()
      } catch (e) {
        console.warn('[community/detail] 删除评论失败:', e)
        showToast({ title: '删除失败，请重试', icon: 'none' })
      }
    }
  })
}

/** 删除游记（仅发布者可操作） */
function handleDelete() {
  if (!post.value) return
  showModal({
    title: '删除游记',
    content: '确定删除该游记吗？删除后不可恢复',
    confirmText: '删除',
    confirmColor: '#e64340',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deletePost(post.value!.id)
        showToast({ title: '已删除', icon: 'success' })
        // 通知列表页刷新
        uni.$emit('journal:deleted', post.value!.id)
        setTimeout(() => {
          const pages = getCurrentPages()
          if (pages.length > 1) {
            uni.navigateBack()
            return
          }
          // 没有上一页（外链/冷启动直接进详情）时的兜底：
          // 原来 switchTab 到 /pages/community/index —— 那不是 tabBar 页，switchTab 会静默失败，
          // 用户会停在被删掉的游记上。改成真实存在的探索 tab。
          uni.switchTab({ url: '/pages/coming/index' })
        }, 500)
      } catch {
        showToast({ title: '删除失败，请重试', icon: 'none' })
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

/* ── 评论区（P1-27）── */
.comment-section {
  margin-top: 40rpx;
  padding-top: 28rpx;
  border-top: 1rpx solid var(--border);
}

.comment-title {
  display: block;
  font-size: var(--fs-title);
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 20rpx;
}

.comment-tip {
  display: block;
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
  padding: 20rpx 0;
}

.comment-item {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid var(--border);
}

.comment-avatar {
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--bg-input);
}

.comment-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-meta);
  color: var(--text-secondary);
}

.comment-main {
  flex: 1;
  min-width: 0;
}

.comment-nickname {
  display: block;
  font-size: var(--fs-meta);
  font-weight: 600;
  color: var(--text-secondary);
}

.comment-content {
  display: block;
  font-size: var(--fs-body);
  color: var(--text-main);
  line-height: 1.7;
  margin-top: 6rpx;
  word-break: break-all;
}

.comment-reply-to {
  display: block;
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
  margin-top: 4rpx;
}

.comment-delete {
  flex-shrink: 0;
  font-size: var(--fs-meta);
  color: var(--danger);
  padding-left: 12rpx;
}

.comment-editor {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 24rpx;
}

.comment-input {
  flex: 1;
  min-width: 0;
  height: 72rpx;
  padding: 0 24rpx;
  background: var(--bg-input);
  border-radius: 36rpx;
  font-size: var(--fs-body);
}

.comment-submit {
  flex-shrink: 0;
  height: 72rpx;
  padding: 0 32rpx;
  display: flex;
  align-items: center;
  border-radius: 36rpx;
  background: linear-gradient(135deg, #48bb88, #2f9d6f);
  color: #fff;
  font-size: var(--fs-body);
  font-weight: 600;

  &.disabled {
    opacity: 0.6;
  }
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
