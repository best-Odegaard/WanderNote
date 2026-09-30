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
      -->
      <view class="section">
        <view class="section-head">
          <text class="section-title">精选推荐</text>
          <text class="section-sub">左右滑动看更多</text>
        </view>
        <scroll-view scroll-x class="rec-scroll" :show-scrollbar="false">
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
                <view class="rec-btn">{{ item.featuredId != null ? '看这趟行程' : '看看详情' }}</view>
              </view>
            </view>
          </view>
        </scroll-view>
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

    <!-- 推荐卡片的景点介绍弹窗（原首页轮播点开的那个） -->
    <transition name="fb-fade">
      <view v-if="bannerIntro" class="popup-root" @tap="closeBannerIntro">
        <view class="popup-mask" />
        <view class="popup-panel" @tap.stop>
          <view class="popup-close" @tap.stop="closeBannerIntro">
            <text class="popup-close-icon">✕</text>
          </view>
          <image class="popup-img" :src="bannerIntro.image" mode="aspectFill" />
          <view class="popup-content">
            <text class="popup-name">{{ bannerIntro.title }}</text>
            <text class="popup-subtitle">{{ bannerIntro.subtitle }}</text>
            <view class="popup-meta">
              <text v-if="bannerIntro.location" class="popup-meta-item">📍 {{ bannerIntro.location }}</text>
              <text v-if="bannerIntro.openTime" class="popup-meta-item">🕐 {{ bannerIntro.openTime }}</text>
              <text v-if="bannerIntro.ticket" class="popup-meta-item">🎫 {{ bannerIntro.ticket }}</text>
            </view>
            <text class="popup-section-title">景点简介</text>
            <text class="popup-desc">{{ bannerIntro.description }}</text>
            <text v-if="bannerIntro.tips" class="popup-tips">💡 {{ bannerIntro.tips }}</text>
          </view>
        </view>
      </view>
    </transition>

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
 * 卡片数据与点击行为沿用原实现：
 *   · 后台配了精选行程 → 展示精选行程卡，点进只读预览页
 *   · 没配（或接口挂了）→ 回退到本页写死的 7 个景点，点击弹景点介绍
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

/** 推荐数据：优先展示后台配置的精选行程；一条都没有时回退到本页写死的景点 */
const banners = computed<HomeBannerItem[]>(() => {
  if (featuredTrips.value.length > 0) {
    return featuredTrips.value.map((f) => ({
      id: f.id,
      title: f.title,
      subtitle: f.subtitle || `${f.city || ''}${f.days ? ` · ${f.days}天` : ''}`,
      imageUrl: f.cover || '',
      featuredId: f.id
    }))
  }
  return HOME_BANNERS
})

/** 轮播图景点介绍（写死在本页，弹窗展示） */
interface BannerIntro extends BannerItem {
  image: string
  location: string
  openTime: string
  ticket: string
  description: string
  tips: string
}

const BANNER_INTROS: Record<string, BannerIntro> = {
  广州塔: {
    id: 1,
    title: '广州塔',
    subtitle: '璀璨夜景 · 城市地标',
    emoji: '🗼',
    image:
      'https://geek003-1348546854.cos.ap-guangzhou.myqcloud.com/AI%E6%96%87%E6%97%85/%E5%9B%BE%E7%89%87/adc7ede837b047cda97938dc0ea9a492%E5%B9%BF%E5%B7%9E%E5%A1%947.jpg',
    location: '广东省广州市海珠区阅江西路222号',
    openTime: '09:30–22:30（以当日公告为准）',
    ticket: '观景门票 150 元起，塔顶设施另行收费',
    description:
      '广州塔昵称"小蛮腰"，总高 600 米，为中国第一、世界第三高的电视观光塔，也是广州的城市地标。塔内设有高空观景台、旋转餐厅、空中邮局，塔顶还有世界最高的横向摩天轮与极限项目"极速云霄"，登顶可 360° 俯瞰珠江两岸的城市夜景。',
    tips: '建议傍晚登塔，日落与珠江夜景一次看够；节假日人流较大，建议提前线上购票。'
  },
  桂林山水: {
    id: 2,
    title: '桂林山水',
    subtitle: '山水甲天下',
    emoji: '🏞️',
    image:
      'https://geek003-1348546854.cos.ap-guangzhou.myqcloud.com/AI%E6%96%87%E6%97%85/%E5%9B%BE%E7%89%87/ea98655cd449495f97e126937c2a09db%E6%A1%82%E6%9E%97%E5%B1%B1%E6%B0%B42.jpg',
    location: '广西壮族自治区桂林市',
    openTime: '各景区开放时间不一，以现场为准',
    ticket: '象鼻山约 55 元，两江四湖游船约 80 元起',
    description:
      '"桂林山水甲天下"，桂林以典型的喀斯特岩溶地貌闻名于世。漓江两岸奇峰倒映、碧水萦回，被誉为"百里画廊"。乘船游漓江、登象鼻山、夜游两江四湖，都是领略桂林山水精华的经典玩法。',
    tips: '春秋两季景色最佳；漓江游船建议提前一天预订，并留意天气变化。'
  },
  成都熊猫: {
    id: 3,
    title: '成都熊猫',
    subtitle: '萌趣之旅 · 巴蜀风情',
    emoji: '🐼',
    image:
      'https://geek003-1348546854.cos.ap-guangzhou.myqcloud.com/AI%E6%96%87%E6%97%85/%E5%9B%BE%E7%89%87/439ea42d1c7c493bbcac42fd6aca9cad%E7%86%8A%E7%8C%AB.png',
    location: '四川省成都市成华区熊猫大道1375号',
    openTime: '07:30–18:00（以景区实际为准）',
    ticket: '成人票 55 元（以景区实际为准）',
    description:
      '成都大熊猫繁育研究基地是世界著名的大熊猫科研与繁育机构，生活着上百只大熊猫，是近距离观察"国宝"的最佳去处。园区竹林掩映，还能遇见小熊猫、孔雀等动物，看大熊猫进食、攀爬、打滚，萌态百出。',
    tips: '大熊猫早晨最活跃，建议开园即入园；园区面积较大，可乘坐观光车游览。'
  },
  杭州西湖: {
    id: 4,
    title: '杭州西湖',
    subtitle: '烟雨江南 · 人间天堂',
    emoji: '🍃',
    image: 'https://upload.wikimedia.org/wikipedia/commons/0/07/20090524_Hangzhou_West_Lake_7531.jpg',
    location: '浙江省杭州市西湖区',
    openTime: '全天开放（部分景点另定）',
    ticket: '免费入园，三潭印月等游船另收费',
    description:
      '西湖是杭州的城市名片、中国十大风景名胜之一。三面环山、一湖碧水，苏堤春晓、断桥残雪、雷峰夕照等"西湖十景"闻名天下，白娘子与许仙的传说更添浪漫色彩。乘船游湖、骑行环湖、漫步苏堤，四季皆有不同风情。',
    tips: '春秋两季景色最佳；清晨或傍晚游客较少，骑行环湖是最惬意的打开方式。'
  },
  重庆洪崖洞: {
    id: 5,
    title: '重庆洪崖洞',
    subtitle: '梦幻夜景 · 千与千寻',
    emoji: '🌉',
    image: 'https://upload.wikimedia.org/wikipedia/commons/6/64/202308_Hongya_Cave_at_night_from_Qiansimen_Bridge.jpg',
    location: '重庆市渝中区嘉陵江滨江路88号',
    openTime: '11:00–23:00（以当日为准）',
    ticket: '免费（需预约）',
    description:
      '洪崖洞是重庆最具特色的吊脚楼建筑群，依山而建、层层叠叠，夜晚灯火通明，仿佛现实版"千与千寻"。站在江边，可同时欣赏两江交汇与千厮门大桥的璀璨夜景，是重庆最出片的打卡地之一。',
    tips: '夜景最佳拍摄时间在 19:30 后；节假日人流大，建议提前线上预约。'
  },
  北京故宫: {
    id: 6,
    title: '北京故宫',
    subtitle: '六百年紫禁城 · 世界遗产',
    emoji: '🏯',
    image: 'https://upload.wikimedia.org/wikipedia/commons/d/da/Beijing_China_Forbidden-City-03.jpg',
    location: '北京市东城区景山前街4号',
    openTime: '08:30–17:00（周一闭馆）',
    ticket: '旺季 60 元，淡季 40 元（需实名预约）',
    description:
      '故宫又名紫禁城，是明清两代皇家宫殿，也是世界上现存规模最大、保存最完整的木质结构古建筑群。红墙黄瓦、金碧辉煌，历经六百年风雨，藏着说不尽的历史与故事，是中轴线上不容错过的世界文化遗产。',
    tips: '需提前在官方渠道实名预约；建议留出半天时间，可租讲解器深入了解历史。'
  },
  张家界: {
    id: 7,
    title: '张家界',
    subtitle: '三千奇峰 · 人间仙境',
    emoji: '⛰️',
    image: 'https://upload.wikimedia.org/wikipedia/commons/4/47/Zhangjiajie_National_Forest_Park.jpg',
    location: '湖南省张家界市武陵源区',
    openTime: '07:00–18:00（以景区实际为准）',
    ticket: '武陵源核心景区约 225 元（含环保车）',
    description:
      '张家界以举世罕见的石英砂岩峰林地貌著称，三千奇峰拔地而起、云雾缭绕时宛如仙境，《阿凡达》中"哈利路亚山"的原型即取景于此。天门山玻璃栈道与 99 道弯盘山公路，更是勇气与风景的双重挑战。',
    tips: '雨后初晴时云海景观最佳；景区面积大，建议规划 2–3 天游玩时间。'
  }
}

/** 推荐卡片列表（写死 7 张，直接由介绍数据生成，保证卡片与弹窗内容一致） */
const HOME_BANNERS: BannerItem[] = Object.values(BANNER_INTROS).map((b) => ({
  id: b.id,
  title: b.title,
  subtitle: b.subtitle,
  emoji: b.emoji,
  imageUrl: b.image
}))

const bannerIntro = ref<BannerIntro | null>(null)

/** 点推荐卡：精选行程进详情页预览，写死的景点走本地弹窗 */
function onBannerTap(item: HomeBannerItem) {
  if (item.featuredId != null) {
    uni.navigateTo({ url: `/pages/trip/detail?featuredId=${item.featuredId}` })
    return
  }
  bannerIntro.value = BANNER_INTROS[item.title] || {
    ...item,
    image: item.imageUrl || '',
    location: '',
    openTime: '',
    ticket: '',
    description: '这里是一处值得探索的宝藏景点，更多精彩等你亲自来发现！',
    tips: ''
  }
}

function closeBannerIntro() {
  bannerIntro.value = null
}

/** 加载后台配置的精选行程（供推荐卡展示）；失败回落空数组 → 回退到写死的景点 */
async function loadFeatured() {
  const list = await withFallback(() => getFeaturedList(), [] as FeaturedTripItem[])
  featuredTrips.value = list
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
