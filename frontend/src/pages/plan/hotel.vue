<template>
  <view class="page">
    <view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="nav-inner" :style="{ height: navHeight + 'px' }">
        <view class="back" @tap="goBack">
          <AppIcon name="chevron-left" :size="34" color="var(--text-body)" />
        </view>
        <view class="nav-text">
          <text class="nav-title">选择酒店</text>
          <text class="nav-sub">{{ city || '目的地' }} · {{ tripDays }}天</text>
        </view>
      </view>
    </view>

    <scroll-view scroll-y class="body" :style="{ paddingTop: navHeight + 'px' }">
      <!-- 档次筛选：默认跟随问卷里的住宿偏好，用户可以在这里改 -->
      <scroll-view scroll-x class="filter-scroll" :show-scrollbar="false">
        <view class="filter-row">
          <view
            v-for="level in levelChoices"
            :key="level"
            class="filter-chip"
            :class="{ active: level === activeLevel }"
            @tap="activeLevel = level"
          >{{ level }}</view>
        </view>
      </scroll-view>

      <text class="list-hint">
        {{ list.length }} 家可选 · 选中后当天的路线会变成「酒店出发 → 各站 → 返回酒店」
      </text>

      <view class="hotel-list">
        <view
          v-for="hotel in list"
          :key="hotel.id"
          class="hotel-card"
          :class="{ picked: pickedId === hotel.id }"
          @tap="pick(hotel)"
        >
          <image class="hotel-cover" :src="hotel.cover" mode="aspectFill" />
          <view class="hotel-info">
            <view class="hotel-head">
              <text class="hotel-name">{{ hotel.name }}</text>
              <view class="radio" :class="{ on: pickedId === hotel.id }" />
            </view>
            <view class="hotel-meta">
              <text class="level-tag">{{ hotel.level }}</text>
              <text class="rating">🌟 {{ hotel.rating.toFixed(1) }}</text>
              <text class="distance">距中心 {{ hotel.distanceKm }}km</text>
            </view>
            <view v-if="hotel.tags.length" class="tag-row">
              <text v-for="tag in hotel.tags" :key="tag" class="tag">{{ tag }}</text>
            </view>
            <text class="hotel-address">📍 {{ hotel.address }}</text>
            <text class="hotel-desc">{{ hotel.desc }}</text>
            <text class="hotel-price">{{ formatHotelPrice(hotel.price) }}</text>
          </view>
        </view>
      </view>

      <view style="height: 260rpx" />
    </scroll-view>

    <!-- 底部按钮：暂不选择（等价于「无要求」）+ 确定 -->
    <view class="footer safe-bottom">
      <button class="btn-skip" @tap="confirm(true)">暂不选择</button>
      <button class="btn-ok" :disabled="!pickedHotel" @tap="confirm(false)">确定</button>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 酒店选择页。
 *
 * 位置：问卷「住宿偏好」→ 本页 → AI 对话页。跳转关系与设计稿的
 * 「问卷提交 → 对话页」一致，只是在中间多了一次「晚上住哪」的选择。
 *
 * 数据来源：utils/hotels.ts 的本地候选表。
 *   后端目前没有酒店列表接口（SlotReadyVO.hotel 只是能力位），所以这里不发请求；
 *   接口就绪后把 getHotelOptions 换成 http.get 即可，本页与行程模型都不用改。
 *
 * 选中结果写入 tripStore.selectedHotel：
 *   · 名称同时回写 currentTrip.hotel（后端 TripPlan 已有字段，保存行程时一起落库）
 *   · 坐标供 pages/trip/route 把当天行程补成「酒店 → 各站 → 酒店」的闭环
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon/AppIcon.vue'
import { useTripStore } from '@/store/trip'
import {
  getHotelOptions,
  formatHotelPrice,
  HOTEL_LEVELS,
  HOTEL_LEVEL_ANY,
  type HotelOption
} from '@/utils/hotels'
import { showToast } from '@/utils/feedback'

const tripStore = useTripStore()

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const navHeight = statusBarHeight + 48

/** 问卷住宿偏好里的「无要求」→ 本页的「不限」 */
const levelChoices = [HOTEL_LEVEL_ANY, ...HOTEL_LEVELS]

const city = ref('')
const activeLevel = ref<string>(HOTEL_LEVEL_ANY)
const pickedId = ref('')

const tripDays = computed(() => tripStore.currentTrip?.days || 1)
const list = computed(() => getHotelOptions(city.value, activeLevel.value))
const pickedHotel = computed(() => list.value.find((h) => h.id === pickedId.value) || null)

onLoad((query) => {
  // 城市优先取 query（刷新/直达也能用），其次取上一页组装好的行程
  city.value = query?.city
    ? decodeURIComponent(String(query.city))
    : tripStore.currentTrip?.toCity || ''
  const level = query?.level ? decodeURIComponent(String(query.level)) : ''
  activeLevel.value = !level || level === '无要求' ? HOTEL_LEVEL_ANY : level

  // 从对话页再进来时回显上次的选择
  const last = tripStore.selectedHotel
  if (last && (!city.value || last.city === city.value)) pickedId.value = last.id
})

function pick(hotel: HotelOption) {
  pickedId.value = pickedId.value === hotel.id ? '' : hotel.id
}

/** @param skip true = 暂不选择（清掉已选，直接进对话） */
function confirm(skip: boolean) {
  if (skip) {
    tripStore.setSelectedHotel(null)
  } else {
    if (!pickedHotel.value) {
      showToast({ title: '先选一家酒店', icon: 'none' })
      return
    }
    tripStore.setSelectedHotel(pickedHotel.value)
  }
  uni.navigateTo({ url: '/pages/ai/chat' })
}

function goBack() {
  uni.navigateBack({
    fail: () => uni.navigateTo({ url: '/pages/plan/survey' })
  })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: linear-gradient(180deg, var(--survey-top) 0%, var(--bg-page) 40%, var(--bg-page) 100%);
}

.nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: var(--glass-bg-strong);
  backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  border-bottom: 1rpx solid var(--glass-border);
}

.nav-inner {
  display: flex;
  align-items: center;
  padding: 0 32rpx;
}

.back {
  display: flex;
  align-items: center;
  margin-right: 16rpx;
  padding: 8rpx;
}

.nav-text {
  flex: 1;
  min-width: 0;
}

.nav-title {
  display: block;
  font-size: var(--fs-subhead);
  font-weight: 700;
  color: var(--text-main);
}

.nav-sub {
  display: block;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  margin-top: 2rpx;
}

.body {
  height: 100vh;
  padding: 0 32rpx;
  box-sizing: border-box;
}

/* ── 档次筛选 ── */
.filter-scroll {
  white-space: nowrap;
  padding: 24rpx 0 8rpx;
}

.filter-row {
  display: inline-flex;
  gap: 16rpx;
}

.filter-chip {
  padding: 14rpx 32rpx;
  border-radius: 999rpx;
  background: var(--bg-input);
  color: var(--text-secondary);
  font-size: var(--fs-meta);
  white-space: nowrap;

  &.active {
    background: var(--brand-grad);
    color: var(--on-brand);
    font-weight: 600;
  }
}

.list-hint {
  display: block;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  line-height: 1.6;
  margin: 12rpx 0 24rpx;
}

/* ── 酒店卡片 ── */
.hotel-card {
  display: flex;
  gap: 24rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  background: var(--bg-card);
  border-radius: 32rpx;
  border: 2rpx solid transparent;
  box-shadow: var(--shadow-sm);

  &.picked {
    border-color: var(--brand);
    background: var(--brand-soft);
  }
}

.hotel-cover {
  width: 200rpx;
  height: 200rpx;
  border-radius: 24rpx;
  flex-shrink: 0;
  background: var(--bg-input);
}

.hotel-info {
  flex: 1;
  min-width: 0;
}

.hotel-head {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
}

.hotel-name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-title);
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.4;
}

/* 单选圆点：选中后填充品牌色 + 白色内点 */
.radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 3rpx solid var(--border-strong);
  flex-shrink: 0;
  margin-top: 4rpx;

  &.on {
    border-color: var(--brand);
    background: var(--brand);
    box-shadow: inset 0 0 0 7rpx var(--bg-card);
  }
}

.hotel-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 8rpx;
  flex-wrap: wrap;
}

.level-tag {
  font-size: var(--fs-caption);
  color: var(--brand-deep);
  background: var(--bg-input);
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
}

.rating,
.distance {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 10rpx;
}

.tag {
  font-size: var(--fs-caption);
  color: var(--text-secondary);
  background: var(--bg-muted);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.hotel-address {
  display: block;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  margin-top: 10rpx;
  line-height: 1.5;
}

.hotel-desc {
  display: block;
  font-size: var(--fs-caption);
  color: var(--text-secondary);
  margin-top: 8rpx;
  line-height: 1.5;
}

.hotel-price {
  display: block;
  font-size: var(--fs-body);
  font-weight: 700;
  color: var(--brand-ink);
  margin-top: 10rpx;
}

/* ── 底部按钮 ── */
.footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 24rpx;
  padding: 20rpx 32rpx calc(20rpx + env(safe-area-inset-bottom));
  background: var(--bg-card);
  border-top: 1rpx solid var(--border);
}

.btn-skip,
.btn-ok {
  height: 96rpx;
  line-height: 96rpx;
  border-radius: 48rpx;
  font-size: var(--fs-title);
  border: none;

  &::after {
    border: none;
  }
}

.btn-skip {
  flex: 1;
  background: var(--bg-card);
  border: 1rpx solid var(--border-strong);
  color: var(--text-main);
}

.btn-ok {
  flex: 2;
  background: var(--brand-grad);
  color: var(--on-brand);
  font-weight: 700;

  &[disabled] {
    opacity: 0.45;
  }
}
</style>
