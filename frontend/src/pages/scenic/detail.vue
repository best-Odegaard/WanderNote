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
        <button class="bar-btn" @tap="toggleCollect">{{ detail.isCollected ? '已收藏' : '收藏' }}</button>
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

const loading = ref(true)
const detail = ref<ScenicItem | null>(null)
const scenicId = ref(0)

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

async function toggleCollect() {
  if (!detail.value) return
  try {
    if (detail.value.isCollected) {
      await uncollectScenic(detail.value.id)
      detail.value.isCollected = false
    } else {
      await collectScenic(detail.value.id)
      detail.value.isCollected = true
    }
    uni.showToast({ title: detail.value.isCollected ? '已收藏' : '已取消', icon: 'none' })
  } catch {
    detail.value.isCollected = !detail.value.isCollected
    uni.showToast({ title: detail.value.isCollected ? '已收藏' : '已取消', icon: 'none' })
  }
}

function addToTrip() {
  uni.navigateTo({ url: '/pages/plan/wizard' })
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
  font-size: 30rpx;
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
  font-size: 40rpx;
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

.rating { color: $warning-color; font-size: 28rpx; }
.city { color: var(--text-secondary); font-size: 26rpx; }
.price { color: $error-color; font-size: 28rpx; font-weight: 500; }

.open-time, .address {
  font-size: 26rpx;
  color: var(--text-secondary);
  margin-top: 12rpx;
  display: block;
}

.section {
  margin-top: 32rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-body);
  margin-bottom: 12rpx;
  display: block;
}

.desc {
  font-size: 28rpx;
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
  font-size: 28rpx;
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
