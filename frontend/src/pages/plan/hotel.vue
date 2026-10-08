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
            @tap="pickLevel(level)"
          >{{ level }}</view>
        </view>
      </scroll-view>

      <!-- 价位筛选：候选价只是参考价，这里按它分档，不代表真实报价 -->
      <scroll-view scroll-x class="filter-scroll" :show-scrollbar="false">
        <view class="filter-row">
          <view
            v-for="bucket in PRICE_BUCKETS"
            :key="bucket.label"
            class="filter-chip"
            :class="{ active: bucket.label === activePrice }"
            @tap="activePrice = bucket.label"
          >{{ bucket.label }}</view>
        </view>
      </scroll-view>

      <!-- 位置筛选：地标来自候选库（运营维护），切一次会带 area 重新问一次接口 -->
      <scroll-view v-if="areaChoices.length > 1" scroll-x class="filter-scroll" :show-scrollbar="false">
        <view class="filter-row">
          <view
            v-for="area in areaChoices"
            :key="area"
            class="filter-chip"
            :class="{ active: area === activeArea }"
            @tap="pickArea(area)"
          >{{ area }}</view>
        </view>
      </scroll-view>

      <text class="list-hint">
        {{ loading ? '正在找附近的酒店…' : `${list.length} 家候选` }}
        · 参考价，以携程为准
        · 选中后当天的路线会变成「酒店出发 → 各站 → 返回酒店」
        <text v-if="!fromApi && !loading" class="offline-hint">（当前为离线候选）</text>
      </text>

      <view class="hotel-list">
        <view
          v-for="hotel in list"
          :key="hotel.id"
          class="hotel-card"
          :class="{ picked: picked?.id === hotel.id }"
          @tap="pick(hotel)"
        >
          <image class="hotel-cover" :src="hotel.cover" mode="aspectFill" />
          <view class="hotel-info">
            <view class="hotel-head">
              <text class="hotel-name">{{ hotel.name }}</text>
              <view class="radio" :class="{ on: picked?.id === hotel.id }" />
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
            <view class="price-row">
              <text class="hotel-price">{{ formatHotelPrice(hotel.price) }}</text>
              <!-- 参考价：不做「有房/可订」承诺，本站没有库存 -->
              <text class="price-note">参考价</text>
              <text class="book-btn" @tap.stop="onBook(hotel)">携程预订 →</text>
            </view>
          </view>
        </view>
      </view>

      <view style="height: 260rpx" />
    </scroll-view>

    <!-- 底部按钮：暂不选择（等价于「无要求」）+ 确定 -->
    <view class="footer safe-bottom">
      <button class="btn-skip" @tap="confirm(true)">暂不选择</button>
      <button class="btn-ok" :disabled="!picked" @tap="confirm(false)">确定</button>
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
 * 数据来源：utils/hotels.ts 的 fetchHotelOptions —— 优先后端 /hotel/search
 *   （候选库可运营维护），接口不可用或候选表还没建时自动退到本地候选表，
 *   两种来源用同一套 hotel_code 与坐标，页面逻辑不用区分。
 *
 * 一条硬约束：交易不在站内完成。
 *   价格全部是**参考价**，页面上必须带「参考价，以携程为准」，
 *   也禁止出现「可订」「有房」这类承诺 —— 我们只有候选库，没有库存。
 *   「携程预订」是跳转外部 H5（utils/deeplink.ts），本站不做下单。
 *
 * 选中结果写入 tripStore.selectedHotel：
 *   · 名称同时回写 currentTrip.hotel（后端 TripPlan 已有字段，保存行程时一起落库）
 *   · 行程已保存（有 id）时立刻调 /hotel/select 落 trip_hotel（含坐标），
 *     这样杀进程再打开行程，「酒店 → 各站 → 酒店」的闭环坐标还在；
 *   · 行程还没保存就先本地暂存，store.saveTrip 保存成功后会自动补写一次。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon/AppIcon.vue'
import { useTripStore } from '@/store/trip'
import { selectHotel } from '@/api/hotel'
import {
  fetchHotelOptions,
  formatHotelPrice,
  HOTEL_LEVELS,
  HOTEL_LEVEL_ANY,
  type HotelOption
} from '@/utils/hotels'
import { openCtripHotel } from '@/utils/deeplink'
import { showToast } from '@/utils/feedback'

const tripStore = useTripStore()

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const navHeight = statusBarHeight + 48

/** 问卷住宿偏好里的「无要求」→ 本页的「不限」 */
const levelChoices = [HOTEL_LEVEL_ANY, ...HOTEL_LEVELS]

/** 价位档：接口不提供价格过滤（参考价本身不是真实报价），在前端按候选价分档，纯展示筛选 */
const PRICE_BUCKETS = [
  { label: '全部价位', min: 0, max: Number.POSITIVE_INFINITY },
  { label: '¥300 以下', min: 0, max: 300 },
  { label: '¥300-600', min: 300, max: 600 },
  { label: '¥600 以上', min: 600, max: Number.POSITIVE_INFINITY }
]

const AREA_ANY = '不限位置'

const city = ref('')
const activeLevel = ref<string>(HOTEL_LEVEL_ANY)
const activePrice = ref(PRICE_BUCKETS[0].label)
const activeArea = ref(AREA_ANY)
/**
 * 从哪来的：'chat'（对话页工具条）/ 'detail'（行程详情更换住宿）/ 空（问卷链路）。
 * 前两者确认后应该 navigateBack 回原页，而不是再 push 一个对话页（否则返回栈越堆越深，
 * 用户连按两次返回还在酒店页）。
 */
const fromPage = ref('')
/** 接口/本地兜底返回的候选（不带位置过滤），用来生成位置 chips */
const sourceList = ref<HotelOption[]>([])
const loading = ref(false)
/** 数据来源：接口 or 本地兜底 —— 兜底时页面提示一句，避免用户以为候选就这么多 */
const fromApi = ref(true)
const picked = ref<HotelOption | null>(null)

const tripDays = computed(() => tripStore.currentTrip?.days || 1)

/** 位置 chips：从候选里取地标去重，最多 6 个 + 「不限位置」 */
const areaChoices = computed(() => {
  const seen: string[] = []
  for (const h of sourceList.value) {
    const lm = (h.nearbyLandmark || '').trim()
    if (lm && !seen.includes(lm)) seen.push(lm)
    if (seen.length >= 6) break
  }
  return [AREA_ANY, ...seen]
})

/** 展示列表：在候选基础上做价位 + 位置过滤 */
const list = computed(() => {
  const bucket = PRICE_BUCKETS.find((b) => b.label === activePrice.value) || PRICE_BUCKETS[0]
  const area = activeArea.value
  return sourceList.value.filter((h) => {
    if (h.price < bucket.min || h.price >= bucket.max) return false
    if (area === AREA_ANY) return true
    return (
      (h.nearbyLandmark || '').includes(area) || h.address.includes(area) || h.name.includes(area)
    )
  })
})

onLoad(async (query) => {
  // 城市优先取 query（刷新/直达也能用），其次取上一页组装好的行程
  city.value = query?.city
    ? decodeURIComponent(String(query.city))
    : tripStore.currentTrip?.toCity || ''
  const level = query?.level ? decodeURIComponent(String(query.level)) : ''
  activeLevel.value = !level || level === '无要求' ? HOTEL_LEVEL_ANY : level
  fromPage.value = query?.from ? String(query.from) : ''

  // 从对话页再进来时回显上次的选择
  const last = tripStore.selectedHotel
  if (last && (!city.value || last.city === city.value)) picked.value = last

  await load()
})

/**
 * 拉候选。
 *
 * 位置切换时把 area 也带给后端（/hotel/search 支持位置筛选，命不中会自动放宽到全城），
 * 这样「运营改了候选库 → 位置筛选结果跟着变」不需要前端同步改逻辑。
 */
async function load() {
  if (loading.value) return
  loading.value = true
  try {
    const result = await fetchHotelOptions({
      city: city.value,
      level: activeLevel.value,
      area: activeArea.value === AREA_ANY ? undefined : activeArea.value,
      checkin: tripStore.currentTrip?.startDate,
      checkout: tripStore.currentTrip?.endDate
    })
    sourceList.value = result.list
    fromApi.value = result.fromApi
    if (result.list.length === 0) {
      showToast({ title: '这个城市暂时没有候选酒店', icon: 'none' })
    }
  } finally {
    loading.value = false
  }
}

async function pickLevel(level: string) {
  if (activeLevel.value === level) return
  activeLevel.value = level
  // 换档次后原来的地标可能一家都不剩，位置筛选回到「不限位置」再拉一次
  activeArea.value = AREA_ANY
  await load()
}

async function pickArea(area: string) {
  if (activeArea.value === area) return
  activeArea.value = area
  await load()
}

function pick(hotel: HotelOption) {
  picked.value = picked.value?.id === hotel.id ? null : hotel
}

/** 携程预订：后端给的深链优先，没有就按同一套模板现场拼（见 utils/deeplink.ts） */
function onBook(hotel: HotelOption) {
  openCtripHotel(hotel.ctripUrl, {
    city: hotel.city || city.value,
    checkin: tripStore.currentTrip?.startDate,
    checkout: tripStore.currentTrip?.endDate,
    hotelName: hotel.name,
    landmark: hotel.nearbyLandmark
  })
}

/** @param skip true = 暂不选择（清掉已选，直接进对话） */
async function confirm(skip: boolean) {
  if (skip) {
    tripStore.setSelectedHotel(null)
    gotoChat()
    return
  }
  const hotel = picked.value
  if (!hotel) {
    showToast({ title: '先选一家酒店', icon: 'none' })
    return
  }

  tripStore.setSelectedHotel(hotel)

  // 行程已保存 → 立刻把住宿落库（含坐标，供详情页/路线页恢复闭环）
  const tripId = tripStore.currentTrip?.id
  if (tripId) {
    try {
      await selectHotel({
        tripId,
        hotelCode: hotel.id,
        name: hotel.name,
        city: hotel.city || city.value,
        level: hotel.level,
        address: hotel.address,
        nearbyLandmark: hotel.nearbyLandmark,
        lng: hotel.lng,
        lat: hotel.lat,
        price: hotel.price,
        checkin: tripStore.currentTrip?.startDate,
        checkout: tripStore.currentTrip?.endDate
      })
    } catch (e) {
      // 落库失败不影响用户继续走：选中状态已在 store 里，路线页照样能闭环
      console.warn('[hotel] 住宿落库失败（本地仍保留选择）:', e)
    }
  }
  gotoChat()
}

function gotoChat() {
  // 从对话页/行程详情进来的，确认后回原页；问卷链路进来的才 push 对话页
  if (fromPage.value === 'chat' || fromPage.value === 'detail') {
    uni.navigateBack({
      fail: () => uni.navigateTo({ url: '/pages/ai/chat' })
    })
    return
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
}

/* 价格行：参考价 + 携程预订入口（跳外部 H5，不在站内下单） */
.price-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 10rpx;
}

.price-note {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  margin-right: auto;
}

.book-btn {
  flex-shrink: 0;
  padding: 8rpx 22rpx;
  border-radius: 999rpx;
  border: 1rpx solid var(--brand);
  color: var(--brand-deep);
  font-size: var(--fs-caption);
  font-weight: 600;
  background: var(--bg-card);
}

/* 离线候选提示：候选来自本地兜底表，不是接口数据 */
.offline-hint {
  color: var(--text-tertiary);
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
