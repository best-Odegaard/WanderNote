<template>
  <view class="page">
    <LoadingView v-if="loading" />
    <!-- 空状态：接口失败或景点不存在时不兜底假数据 -->
    <view v-else-if="!detail" class="empty-state">
      <text class="empty-emoji">🏔️</text>
      <text class="empty-text">景点信息获取失败</text>
    </view>
    <view v-else-if="detail">
      <swiper class="banner" circular indicator-dots>
        <swiper-item v-for="(img, i) in images" :key="i">
          <image :src="img" mode="aspectFill" class="banner-img" />
        </swiper-item>
      </swiper>

      <view class="content">
        <text class="name">{{ detail.name }}</text>
        <view class="meta">
          <text class="rating">⭐ {{ formatRating(detail.rating) }}</text>
          <text class="city">{{ detail.city }}</text>
          <text v-if="detail.price !== undefined" class="price">{{ detail.price === 0 ? '免费' : formatPrice(detail.price) }}</text>
        </view>
        <text v-if="detail.openTime" class="open-time">开放时间：{{ detail.openTime }}</text>
        <text v-if="detail.address" class="address">📍 {{ detail.address }}</text>

        <view v-if="detail.reason" class="section">
          <text class="section-title">推荐理由</text>
          <text class="desc">{{ detail.reason }}</text>
        </view>

        <view class="section">
          <text class="section-title">景点简介</text>
          <text class="desc">{{ detail.description || '暂无简介' }}</text>
        </view>
      </view>

      <view class="bottom-bar safe-bottom">
        <!-- 收藏/加入行程都是需要登录的真实动作，请求中禁用防连点（连点会把状态与服务端搞反） -->
        <button class="bar-btn" :disabled="collecting" @tap="toggleCollect">{{ detail.isCollected ? '已收藏' : '收藏' }}</button>
        <button class="bar-btn primary" @tap="addToTrip">加入行程</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import { getScenicDetail, collectScenic, uncollectScenic } from '@/api/scenic'
import { formatRating, formatPrice } from '@/utils/format'
import type { ScenicItem } from '@/api/scenic'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'
import { useTripStore } from '@/store/trip'
import { showToast } from '@/utils/feedback'

const loading = ref(true)
const detail = ref<ScenicItem | null>(null)
const scenicId = ref(0)
/** 收藏请求进行中：防连点（乐观更新只在前端，连点会把状态与服务端搞反） */
const collecting = ref(false)

const tripStore = useTripStore()

/** 需要登录才能做的动作：未登录先跳登录（带 redirect 回跳，回来还能接着点） */
function ensureLogin(): boolean {
  if (isLoggedIn()) return true
  showToast({ title: '请先登录', icon: 'none' })
  setTimeout(() => redirectToLogin(), 300)
  return false
}

const images = computed(() => {
  if (!detail.value) return []
  return detail.value.images?.length ? detail.value.images : [detail.value.cover]
})

onMounted(async () => {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as { options?: { id?: string } }
  scenicId.value = Number(page.options?.id || 0)

  try {
    detail.value = await getScenicDetail(scenicId.value)
  } catch (e) {
    console.warn('[scenic/detail] 获取景点详情失败:', e)
    // 不兜底 mock 数据，展示空状态
  }
  loading.value = false
})

/**
 * 收藏/取消收藏景点。
 *
 * 原来是「乐观更新 + catch 里再翻一次并提示成功」：未登录点收藏，接口 401 失败，
 * 界面却变成「已收藏」还提示成功 —— 本地状态与服务端相反，用户以为收藏成功，
 * 去「我的收藏」却找不到。现在：失败回滚到原值 + 如实提示「操作失败，请重试」，
 * 并在动作前做登录校验（对照 community/detail.vue、activity/detail.vue 的写法）。
 */
async function toggleCollect() {
  if (!detail.value || collecting.value) return
  if (!ensureLogin()) return

  const before = detail.value.isCollected
  const target = !before
  // 乐观更新 UI：先动界面，失败再回滚
  detail.value.isCollected = target
  collecting.value = true
  try {
    if (target) {
      await collectScenic(detail.value.id)
    } else {
      await uncollectScenic(detail.value.id)
    }
    showToast({ title: target ? '已收藏' : '已取消', icon: 'none' })
  } catch (e) {
    // 失败必须回滚：不能让界面停在一个服务端并不存在的状态上，更不能提示成功
    detail.value.isCollected = before
    console.warn('[scenic/detail] 收藏失败:', e)
    showToast({ title: '操作失败，请重试', icon: 'none' })
  } finally {
    collecting.value = false
  }
}

/**
 * 加入行程：带上景点城市与景点上下文再进规划向导。
 *
 * 原来只 `navigateTo('/pages/plan/wizard')`：无参数、也不校验登录，用户到了向导页
 * 城市是空的、也不知道自己为什么来，等于把刚在景点页产生的转化意图清零。
 * 这里对照 community/detail.vue 的写法补齐：
 *   · `?city=` 让向导页预填目的地（wizard.vue 的 onLoad 读 options.city）
 *   · `tripStore.pendingTripContext` 让 AI 第一轮就知道用户是照着这个景点来的
 */
function addToTrip() {
  if (!detail.value) return
  if (!ensureLogin()) return

  const city = detail.value.city?.trim() || ''
  const ctxParts: string[] = [`参考景点：《${detail.value.name}》`]
  if (city) ctxParts.push(`所在城市：${city}`)
  if (detail.value.reason) {
    ctxParts.push(`推荐理由：${detail.value.reason.replace(/\s+/g, ' ').slice(0, 100)}`)
  }
  tripStore.pendingTripContext = ctxParts.join('；')

  uni.navigateTo({
    url: city ? `/pages/plan/wizard?city=${encodeURIComponent(city)}` : '/pages/plan/wizard'
  })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

/* 空状态：景点获取失败时的提示 */
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

.banner {
  height: 500rpx;
}

.banner-img {
  width: 100%;
  height: 100%;
}

.content {
  padding: 32rpx;
  padding-bottom: 160rpx;
}

.name {
  font-size: var(--fs-heading);
  font-weight: 600;
  color: var(--text-body);
  display: block;
}

.meta {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 16rpx;
}

.rating { color: $warning-color; font-size: var(--fs-body); }
.city { color: var(--text-secondary); font-size: var(--fs-body); }
.price { color: $error-color; font-size: var(--fs-body); font-weight: 500; }

.open-time, .address {
  font-size: var(--fs-body);
  color: var(--text-secondary);
  margin-top: 12rpx;
  display: block;
}

.section {
  margin-top: 32rpx;
}

.section-title {
  font-size: var(--fs-title);
  font-weight: 600;
  color: var(--text-body);
  margin-bottom: 12rpx;
  display: block;
}

.desc {
  font-size: var(--fs-body);
  color: var(--text-secondary);
  line-height: 1.8;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 20rpx;
  padding: 20rpx 32rpx;
  background: var(--bg-card);
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
}

.bar-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  font-size: var(--fs-body);
  border-radius: 40rpx;
  background: var(--bg-input);
  color: var(--text-body);
  border: none;

  &::after { border: none; }

  &.primary {
    background: $primary-color;
    color: #fff;
  }
}
</style>
