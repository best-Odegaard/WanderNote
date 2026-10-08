<template>
  <view class="page page-with-tabbar">
    <view class="header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="header-inner">
        <text class="title">探索</text>
        <view class="publish-btn" @tap="goPublish">
          <AppIcon name="edit" :size="26" color="var(--brand-ink)" />
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
      <!--
        精选推荐：从首页轮播搬过来的。
        轮播一次只能看一张，还占掉首屏最大的一块位置；换成横向卡片后
        一屏能扫到两三张，也更符合「探索」这个 tab 的用法。
        没有数据时给空态 + 重试，不再用写死的景点兜底（见 script 里的注释）。
      -->
      <view class="section">
        <view class="section-head">
          <text class="section-title">精选推荐</text>
          <text v-if="banners.length > 0" class="section-sub">左右滑动看更多</text>
        </view>
        <view v-if="featuredLoading && banners.length === 0" class="section-empty">
          <text class="section-empty-text">正在加载精选行程…</text>
        </view>
        <view v-else-if="banners.length === 0" class="section-empty">
          <text class="section-empty-text">
            {{ featuredFailed ? '精选行程没加载出来，检查网络后重试' : '暂时还没有精选行程' }}
          </text>
          <view class="section-empty-btn" @tap="loadFeatured">重试</view>
        </view>
        <scroll-view v-else scroll-x class="rec-scroll" :show-scrollbar="false">
          <view class="rec-row">
            <view
              v-for="item in banners"
              :key="item.id"
              class="rec-card"
              @tap="onBannerTap(item)"
            >
              <image v-if="item.imageUrl" class="rec-bg" :src="item.imageUrl" mode="aspectFill" />
              <view class="rec-mask" />
              <view class="rec-text">
                <text class="rec-title">{{ item.title }}</text>
                <text class="rec-sub">{{ item.subtitle }}</text>
                <view class="rec-btn">看这趟行程</view>
              </view>
            </view>
          </view>
        </scroll-view>
      </view>

      <!--
        发现更多：景点 / 活动 / 社区列表的真实入口。
        在这之前这三个模块（以及带分页、收藏、报名的完整页面）在 App 里
        没有任何跳转指向它们，只能靠猜 URL 打开 —— 页面注册了却到不了。
      -->
      <view class="section">
        <text class="section-title">发现更多</text>
        <view class="entry-row">
          <view class="entry-card" @tap="goScenicList">
            <text class="entry-emoji">🏞️</text>
            <text class="entry-label">热门景点</text>
          </view>
          <view class="entry-card" @tap="goActivityList">
            <text class="entry-emoji">🎪</text>
            <text class="entry-label">近期活动</text>
          </view>
          <view class="entry-card" @tap="goCommunity">
            <text class="entry-emoji">📓</text>
            <text class="entry-label">社区广场</text>
          </view>
        </view>
      </view>

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
/**
 * 探索。
 *
 * 首页改成对话界面后，原来的轮播图搬到这里，并以「横向卡片」的形式呈现：
 * 轮播一次只见一张、且抢占首屏最贵的位置，卡片一屏能扫到两三张，
 * 更符合「探索」这个入口的用途。
 *
 * 内容来源（三条都是真实数据，页面内不再写死任何内容）：
 *   · 精选推荐 → `GET /featured/list`（后台配置的精选行程），点进只读预览页
 *   · 发现更多 → 景点 / 活动 / 社区列表的入口
 *   · 社区发现 → `GET /journal/list` 的瀑布流
 * 接口失败一律显示空态 + 重试，不用硬编码内容兜底（原因见 banners 的注释）。
 */
import { ref, computed } from 'vue'
import AppTabBar from '@/components/AppTabBar/AppTabBar.vue'
import CommunityCard from '@/components/CommunityCard/CommunityCard.vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import { useTabBarPage } from '@/hooks/useTabBarPage'
import { withFallback } from '@/utils/mock'
import { getCommunityList } from '@/api/community'
import { getFeaturedList, type FeaturedTripItem } from '@/api/featured'
import type { BannerItem } from '@/api/home'
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

// ── 推荐卡片（原首页轮播） ──
/** 轮播项：在通用 Banner 上扩展一个精选行程标记 */
interface HomeBannerItem extends BannerItem {
  /** 有值时说明这条是「精选行程」，点击进入行程预览而不是弹景点简介 */
  featuredId?: number
}

/** 后台配置的精选行程（一整个城市的完整行程） */
const featuredTrips = ref<FeaturedTripItem[]>([])

/**
 * 推荐数据 = 后台配置的精选行程。
 *
 * 这里**故意不再回退到写死的景点**：以前接口失败或后台没配内容时，
 * 页面会展示 7 张精美卡片，而卡片里的门票、开放时间（「观景门票 150 元起」等）
 * 全是硬编码的实况信息 —— 用户分不清这是官方内容还是示例，
 * 接口故障也会被「看起来有内容」掩盖，空态永远不出现，排障时拿不到任何信号。
 * 现在没有数据就老实显示空态 + 重试。
 */
const banners = computed<HomeBannerItem[]>(() =>
  featuredTrips.value.map((f) => ({
    id: f.id,
    title: f.title,
    subtitle: f.subtitle || `${f.city || ''}${f.days ? ` · ${f.days}天` : ''}`,
    imageUrl: f.cover || '',
    featuredId: f.id
  }))
)

/** 精选推荐加载中 / 是否失败（空态文案据此区分「确实没有内容」与「没拉到」） */
const featuredLoading = ref(false)
const featuredFailed = ref(false)

/** 点推荐卡：进精选行程的只读预览页 */
function onBannerTap(item: HomeBannerItem) {
  if (item.featuredId == null) return
  uni.navigateTo({ url: `/pages/trip/detail?featuredId=${item.featuredId}` })
}

/** 加载后台配置的精选行程；失败时如实标记（不再回落写死内容） */
async function loadFeatured() {
  featuredLoading.value = true
  try {
    featuredTrips.value = await getFeaturedList()
    featuredFailed.value = false
  } catch (e) {
    console.warn('[coming] 精选行程加载失败:', e)
    featuredTrips.value = []
    featuredFailed.value = true
  } finally {
    featuredLoading.value = false
  }
}

/** 内容页入口：景点 / 活动 / 社区（原先这些页面在 App 里没有任何入口） */
function goScenicList() {
  uni.navigateTo({ url: '/pages/scenic/list' })
}
function goActivityList() {
  uni.navigateTo({ url: '/pages/activity/list' })
}
function goCommunity() {
  uni.navigateTo({ url: '/pages/community/index' })
}

// Tab 页每次显示都取首屏：首次进入正常加载，已有数据则静默刷新，
// 保证从详情页点赞/收藏返回后列表计数保持一致
useTabBarPage(2, () => {
  loadFeatured()
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
  position: relative;
}

.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  // 玻璃顶栏
  background: var(--glass-bg-strong);
  backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  border-bottom: 1rpx solid var(--glass-border);
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
  font-size: 46rpx;
  font-weight: 800;
  color: var(--text-main);
  letter-spacing: -0.6rpx;
}

.publish-btn {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--brand-ink);
  padding: 12rpx 26rpx;
  // 玻璃胶囊：比裸文字更像一个可点的控件
  background: var(--brand-soft);
  border: 1rpx solid var(--glass-border);
  border-radius: $radius-pill;
  transition: transform $dur-fast $ease-out;

  &:active {
    transform: scale(0.94);
  }
}

.scroll-body {
  height: 100vh;
  box-sizing: border-box;
}

.section {
  padding: $page-padding;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.section-title {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--text-main);
  display: block;
  margin-bottom: 24rpx;
  letter-spacing: -0.3rpx;
}

.section-head .section-title {
  margin-bottom: 0;
}

.section-sub {
  font-size: 24rpx;
  color: var(--text-tertiary);
}

/* ── 精选推荐空态 / 重试 ── */
.section-empty {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  background: var(--bg-card);
  border: 1rpx solid var(--border);
}

.section-empty-text {
  flex: 1;
  font-size: 26rpx;
  color: var(--text-secondary);
}

.section-empty-btn {
  flex-shrink: 0;
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  border: 1rpx solid var(--brand);
  color: var(--brand-ink);
  font-size: 26rpx;
  font-weight: 600;
}

/* ── 发现更多：景点 / 活动 / 社区列表入口 ── */
.entry-row {
  display: flex;
  gap: 20rpx;
}

.entry-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 28rpx 0;
  border-radius: 24rpx;
  background: var(--bg-card);
  border: 1rpx solid var(--border);
  box-shadow: var(--shadow-sm);
}

.entry-emoji {
  font-size: 44rpx;
}

.entry-label {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--text-main);
}

/* ── 推荐卡片：横向滚动，一屏能看到下一张的边，暗示可滑动 ── */
.rec-scroll {
  white-space: nowrap;
}

.rec-row {
  display: inline-flex;
  gap: 20rpx;
}

.rec-card {
  position: relative;
  width: 520rpx;
  height: 320rpx;
  flex-shrink: 0;
  border-radius: $card-radius-lg;
  overflow: hidden;
  box-shadow: var(--shadow-lg);

  &:active {
    opacity: 0.92;
  }
}

.rec-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.rec-mask {
  position: absolute;
  inset: 0;
  // 更深的青绿到透明：保证白字在任何图片上都可读
  background: linear-gradient(
    100deg,
    rgba(6, 78, 59, 0.82) 0%,
    rgba(6, 78, 59, 0.45) 46%,
    rgba(6, 78, 59, 0.08) 100%
  );
}

.rec-text {
  position: absolute;
  left: 36rpx;
  right: 36rpx;
  bottom: 32rpx;
}

.rec-title {
  font-size: 40rpx;
  font-weight: 800;
  color: #fff;
  display: block;
  letter-spacing: -0.5rpx;
  text-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.25);
}

.rec-sub {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.88);
  margin-top: 6rpx;
  display: block;
}

.rec-btn {
  display: inline-block;
  margin-top: 18rpx;
  padding: 10rpx 24rpx;
  border-radius: $radius-pill;
  background: rgba(255, 255, 255, 0.9);
  color: #064e3b;
  font-size: 24rpx;
  font-weight: 600;
}

.waterfall {
  display: flex;
  gap: 20rpx;
}

.column {
  flex: 1;
  min-width: 0;
}

.load-more {
  text-align: center;
  padding: 28rpx;
  font-size: 24rpx;
  color: var(--text-placeholder);
}

/* ── 景点介绍弹窗（原首页轮播的弹窗，随内容一起搬过来） ── */
.popup-root {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: flex-end;
}

.popup-mask {
  position: absolute;
  inset: 0;
  background: rgba(4, 32, 26, 0.48);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

/* 退场动画：仅 leave 分支，入场仍由 .popup-panel 自身动画负责 */
.fb-fade-leave-active {
  transition: opacity 0.18s ease;
}

.fb-fade-leave-to {
  opacity: 0;
}

.popup-panel {
  position: relative;
  width: 100%;
  max-height: 80vh;
  background: var(--glass-bg-strong);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border-radius: 48rpx 48rpx 0 0;
  border-top: 1rpx solid var(--glass-border);
  overflow: hidden;
  animation: popupSlideUp 0.32s $ease-out;
  display: flex;
  flex-direction: column;
  box-shadow: 0 -12rpx 48rpx rgba(4, 32, 26, 0.28);
}

@keyframes popupSlideUp {
  from {
    opacity: 0;
    transform: translateY(80rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.popup-close {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  z-index: 5;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.34);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform $dur-fast $ease-out;

  &:active {
    transform: scale(0.9);
  }
}

.popup-close-icon {
  color: #fff;
  font-size: 32rpx;
  line-height: 1;
}

.popup-img {
  width: 100%;
  height: 400rpx;
  flex-shrink: 0;
}

.popup-content {
  padding: 36rpx 32rpx 48rpx;
  overflow-y: auto;
}

.popup-name {
  font-size: 42rpx;
  font-weight: 800;
  color: var(--text-main);
  display: block;
  letter-spacing: -0.5rpx;
}

.popup-subtitle {
  font-size: 26rpx;
  color: var(--brand-ink);
  margin-top: 8rpx;
  display: block;
  font-weight: 500;
}

.popup-meta {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-top: 26rpx;
  padding: 24rpx 26rpx;
  background: var(--bg-input);
  border-radius: $radius-lg;
}

.popup-meta-item {
  font-size: 24rpx;
  color: var(--text-secondary);
  line-height: 1.5;
}

.popup-section-title {
  display: block;
  margin-top: 34rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: var(--text-main);
}

.popup-desc {
  display: block;
  margin-top: 14rpx;
  font-size: 27rpx;
  color: var(--text-secondary);
  line-height: 1.8;
}

.popup-tips {
  display: block;
  margin-top: 26rpx;
  padding: 22rpx 26rpx;
  background: var(--brand-soft);
  border-left: 6rpx solid var(--brand);
  border-radius: $radius-md;
  font-size: 24rpx;
  color: var(--text-body);
  line-height: 1.65;
}
</style>
