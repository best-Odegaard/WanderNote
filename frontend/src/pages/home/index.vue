<template>
  <view class="page page-with-tabbar">
    <view class="top-gradient" />

    <view class="header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="header-inner">
        <text class="brand-name">旅行计划</text>
        <view class="search-wrap">
          <AppIcon name="search" :size="30" color="#58a883" class="search-icon" />
          <input
            class="search-input"
            placeholder="搜索目的地..."
            placeholder-class="search-placeholder"
            confirm-type="search"
            @confirm="onSearch"
          />
        </view>
        <view class="notify-btn" @tap="onNotify">
          <AppIcon name="bell" :size="36" color="var(--text-body)" />
        </view>
      </view>
    </view>

    <scroll-view scroll-y class="scroll-body" :style="{ paddingTop: headerHeight + 'px' }">
      <swiper class="banner-swiper" circular autoplay :interval="4000" @change="onBannerChange">
        <swiper-item v-for="item in banners" :key="item.id">
          <view class="banner-card" @tap="onBannerTap(item)">
            <!-- 新增背景图片 -->
            <image class="banner-bg-img" :src="item.imageUrl" mode="aspectFill"></image>
            <!-- 渐变遮罩 -->
            <view class="banner-mask"></view>
            <view class="banner-text">
              <text class="banner-title">{{ item.title }}</text>
              <text class="banner-sub">{{ item.subtitle }}</text>
              <view class="banner-btn" @tap.stop="onBannerTap(item)">{{ item.featuredId ? '查看行程' : '立即探索' }}</view>
            </view>
            <text class="banner-deco">{{ item.emoji }}</text>
          </view>
        </swiper-item>
      </swiper>
      <view class="banner-dots">
        <view v-for="(_, i) in banners" :key="i" class="dot" :class="{ active: bannerIndex === i }" />
      </view>

      <!-- 我的行程：只取最近 3 条，卡片样式见 MyPlanCard -->
      <view class="section">
        <view class="section-head">
          <text class="section-title">我的行程</text>
          <view v-if="trips.length > 0" class="section-link" @tap="goTripList">
            <text>查看全部</text>
            <AppIcon name="chevron-right" :size="26" color="#58a883" />
          </view>
        </view>

        <LoadingView v-if="tripsLoading" />
        <EmptyState
          v-else-if="trips.length === 0"
          icon="calendar"
          title="还没有行程"
          description="聊聊你想去哪，让 AI 帮你排一份"
          button-text="创建行程"
          @action="goCreateTrip"
        />
        <view v-else class="trip-list">
          <MyPlanCard
            v-for="(trip, i) in trips"
            :key="String(trip.id)"
            :plan="trip"
            :index="i"
            @tap="viewTrip(trip)"
          />
        </view>
      </view>

      <view style="height: 160rpx" />
    </scroll-view>

    <!-- 轮播图景点介绍弹窗 -->
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

    <!-- #ifndef MP-WEIXIN -->
    <AppTabBar />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import AppTabBar from '@/components/AppTabBar/AppTabBar.vue'
import MyPlanCard from '@/components/MyPlanCard/MyPlanCard.vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import EmptyState from '@/components/EmptyState/EmptyState.vue'
import { useTabBarPage } from '@/hooks/useTabBarPage'
import { withFallback } from '@/utils/mock'
import { getMyTrips, type TripPlan } from '@/api/trip'
import { getFeaturedList, type FeaturedTripItem } from '@/api/featured'
import { toMyPlanItem, type MyPlanItem } from '@/utils/tripCard'
import { useLogin } from '@/hooks/useLogin'
import type { BannerItem } from '@/api/home'

const { checkLogin } = useLogin()

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const headerHeight = statusBarHeight + 56

// ── 首页数据 ──
/** 轮播项：在通用 Banner 上扩展一个精选行程标记 */
interface HomeBannerItem extends BannerItem {
  /** 有值时说明这条是「精选行程」，点击进入行程预览而不是弹景点简介 */
  featuredId?: number
}

/** 后台配置的精选行程（一整个城市的完整行程） */
const featuredTrips = ref<FeaturedTripItem[]>([])

/**
 * 轮播数据：优先展示后台配置的精选行程；
 * 一条都没配（或接口挂了）时回退到本页写死的 7 个景点，保证首页不空。
 */
const banners = computed<HomeBannerItem[]>(() => {
  if (featuredTrips.value.length > 0) {
    return featuredTrips.value.map((f) => ({
      id: f.id,
      title: f.title,
      subtitle: f.subtitle || `${f.city || ''}${f.days ? ` · ${f.days}天` : ''}`,
      emoji: '🧭',
      imageUrl: f.cover || '',
      featuredId: f.id
    }))
  }
  return HOME_BANNERS
})

const bannerIndex = ref(0)

// ── 轮播图景点介绍（写死在本页，弹窗展示）──
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

/** 轮播图列表（写死 7 张，直接由介绍数据生成，保证轮播与弹窗内容一致） */
const HOME_BANNERS: BannerItem[] = Object.values(BANNER_INTROS).map((b) => ({
  id: b.id,
  title: b.title,
  subtitle: b.subtitle,
  emoji: b.emoji,
  imageUrl: b.image
}))

const bannerIntro = ref<BannerIntro | null>(null)

/** 点轮播：精选行程进详情页预览，写死的景点仍走本地弹窗 */
function onBannerTap(item: HomeBannerItem) {
  if (item.featuredId != null) {
    uni.navigateTo({ url: `/pages/trip/detail?featuredId=${item.featuredId}` })
    return
  }
  showBannerIntro(item)
}

function showBannerIntro(item: BannerItem) {
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

// ── 我的行程：首页只展示最近 3 条 ──
const TRIP_PREVIEW_COUNT = 3

const allTrips = ref<MyPlanItem[]>([])
const tripsLoading = ref(false)

const trips = computed(() => allTrips.value.slice(0, TRIP_PREVIEW_COUNT))

/** 加载后台配置的精选行程（供轮播展示）；失败回落空数组 → 轮播回退到本页写死的景点 */
async function loadFeatured() {
  const list = await withFallback(() => getFeaturedList(), [] as FeaturedTripItem[])
  featuredTrips.value = list
}

async function loadTrips() {
  if (tripsLoading.value) return
  tripsLoading.value = true
  try {
    // 接口失败回落空列表（展示空态），不使用任何 mock 数据
    const list = await withFallback(() => getMyTrips(), [] as TripPlan[])
    allTrips.value = list.map((trip, i) => toMyPlanItem(trip, i))
  } finally {
    tripsLoading.value = false
  }
}

function viewTrip(trip: MyPlanItem) {
  uni.navigateTo({ url: `/pages/trip/detail?id=${trip.id}` })
}

function goTripList() {
  uni.switchTab({ url: '/pages/trip/index' })
}

function goCreateTrip() {
  if (!checkLogin()) return
  uni.navigateTo({ url: '/pages/plan/wizard' })
}

// Tab 页每次显示都重新拉取，从创建/编辑行程返回后列表能及时更新
useTabBarPage(0, () => {
  loadFeatured()
  loadTrips()
})

function onBannerChange(e: { detail: { current: number } }) {
  bannerIndex.value = e.detail.current
}

function onSearch() {
  uni.showModal({
    title: '提示',
    content: '由于数据不完善，暂时不对外开放',
    showCancel: false,
    confirmText: '知道了'
  })
}

function onNotify() {
  uni.showToast({ title: '暂无新消息', icon: 'none' })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
  position: relative;
}

.top-gradient {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 320rpx;
  background: linear-gradient(180deg, rgba(168, 230, 207, 0.3), transparent);
  pointer-events: none;
  z-index: 1;
}

.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 16rpx;
  height: 112rpx;
  padding: 0 32rpx 16rpx;
}

.brand-name {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--text-body);
  flex-shrink: 0;
  max-width: 140rpx;
}

.search-wrap {
  flex: 1;
  position: relative;
  height: 72rpx;
  background-color: var(--bg-card);
  border-radius: 9999rpx;
  display: flex;
  align-items: center;
  padding: 0 30rpx 0 64rpx;
  box-shadow: 0 2rpx 10rpx rgba(88, 168, 131, 0.12);
  border: none;
  transition: all 0.2s ease;
  // 点击按压效果
  &:active {
    transform: scale(0.98);
    box-shadow: 0 1rpx 5rpx rgba(88, 168, 131, 0.08);
  }
}

.search-icon {
  position: absolute;
  left: 24rpx;
  display: flex;
  align-items: center;
}

.search-input {
  flex: 1;
  font-size: 28rpx;
  color: var(--text-main);
  height: 72rpx;
}

:deep(.search-placeholder) {
  color: var(--text-placeholder);
}

.notify-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: var(--bg-card);
  box-shadow: 0 2rpx 10rpx rgba(88, 168, 131, 0.12);
  transition: transform 0.15s ease;

  &:active {
    transform: scale(0.92);
  }
}

.scroll-body {
  height: 100vh;
  box-sizing: border-box;
  padding: 0 32rpx;
  position: relative;
  z-index: 2;
  // 新增这一行，防止底部内容被tab栏遮挡
  padding-bottom: calc(130rpx + env(safe-area-inset-bottom));
}

.banner-swiper {
  height: 320rpx;
  margin-top: 16rpx;
  border-radius: $card-radius-lg;
  overflow: hidden;
}

.banner-card {
  height: 320rpx;
  border-radius: $card-radius-lg;
  padding: 48rpx;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: none;
}

.banner-bg-img {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
}
.banner-mask {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, rgba(42, 163, 117, 0.72), rgba(42, 163, 117, 0.28));
  z-index: 2;
}
.banner-text {
  position: relative;
  z-index: 3;
}

.banner-title {
  font-size: 40rpx;
  font-weight: 700;
  color: #fff;
  display: block;
}

.banner-sub {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.9);
  margin-top: 8rpx;
  display: block;
}

.banner-btn {
  margin-top: 24rpx;
  display: inline-block;
  padding: 12rpx 32rpx;
  background: rgba(255, 255, 255, 0.2);
  border: 1rpx solid rgba(255, 255, 255, 0.3);
  border-radius: 999rpx;
  font-size: 22rpx;
  color: #fff;
  align-self: flex-start;
}

.banner-deco {
  position: absolute;
  right: 32rpx;
  bottom: 16rpx;
  font-size: 120rpx;
  opacity: 0.3;
  z-index: 3;
}

.banner-dots {
  display: flex;
  justify-content: center;
  gap: 12rpx;
  margin: 20rpx 0 8rpx;
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 999rpx;
  background: var(--bg-input);

  &.active {
    width: 32rpx;
    background: $mint-primary;
  }
}

.section {
  margin-top: 40rpx;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--text-main);
  display: block;
  margin-bottom: 20rpx;
}

.section-head .section-title {
  margin-bottom: 0;
}

.section-link {
  font-size: 24rpx;
  color: $mint-primary;
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
}

/* 轮播图景点介绍弹窗 */
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
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(8px);
}

.popup-panel {
  position: relative;
  width: 100%;
  max-height: 78vh;
  background: var(--bg-card);
  border-radius: 40rpx 40rpx 0 0;
  overflow: hidden;
  animation: popupSlideUp 0.28s ease-out;
  display: flex;
  flex-direction: column;
}

@keyframes popupSlideUp {
  from {
    opacity: 0;
    transform: translateY(60rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.popup-close {
  position: absolute;
  top: 20rpx;
  right: 20rpx;
  z-index: 5;
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
}

.popup-close-icon {
  color: #fff;
  font-size: 32rpx;
  line-height: 1;
}

.popup-img {
  width: 100%;
  height: 380rpx;
  flex-shrink: 0;
}

.popup-content {
  padding: 32rpx;
  overflow-y: auto;
}

.popup-name {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--text-body);
  display: block;
}

.popup-subtitle {
  font-size: 26rpx;
  color: $mint-primary;
  margin-top: 8rpx;
  display: block;
}

.popup-meta {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  margin-top: 24rpx;
  padding: 20rpx 24rpx;
  background: var(--bg-input);
  border-radius: 20rpx;
}

.popup-meta-item {
  font-size: 24rpx;
  color: var(--text-secondary);
  line-height: 1.5;
}

.popup-section-title {
  display: block;
  margin-top: 32rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-body);
}

.popup-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 27rpx;
  color: var(--text-secondary);
  line-height: 1.8;
}

.popup-tips {
  display: block;
  margin-top: 24rpx;
  padding: 20rpx 24rpx;
  background: rgba(168, 230, 207, 0.35);
  border-radius: 20rpx;
  font-size: 24rpx;
  color: var(--text-body);
  line-height: 1.6;
}
</style>
