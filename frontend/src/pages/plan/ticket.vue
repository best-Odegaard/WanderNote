<template>
  <view class="page">
    <view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="nav-inner" :style="{ height: navHeight + 'px' }">
        <view class="back" @tap="goBack">
          <AppIcon name="chevron-left" :size="34" color="var(--text-body)" />
        </view>
        <view class="nav-text">
          <text class="nav-title">订票</text>
          <text class="nav-sub">{{ routeText }}</text>
        </view>
      </view>
    </view>

    <scroll-view scroll-y class="body" :style="{ paddingTop: navHeight + 'px' }">
      <!-- 票种：火车 / 飞机 -->
      <view class="seg-row">
        <view
          v-for="t in TYPES"
          :key="t.value"
          class="seg"
          :class="{ active: t.value === type }"
          @tap="pickType(t.value)"
        >{{ t.label }}</view>
      </view>

      <!-- 单程 / 往返 -->
      <view class="seg-row">
        <view
          v-for="m in MODES"
          :key="String(m.round)"
          class="seg"
          :class="{ active: m.round === roundTrip }"
          @tap="pickMode(m.round)"
        >{{ m.label }}</view>
      </view>

      <!-- 去程 / 返程：往返时两段各自独立选一条，也可以只选一段 -->
      <view v-if="roundTrip" class="seg-row">
        <view
          v-for="d in DIRECTIONS"
          :key="d.value"
          class="seg"
          :class="{ active: d.value === segment }"
          @tap="segment = d.value"
        >
          {{ d.label }}
          <text class="seg-picked">{{ pickedLabel(d.value) }}</text>
        </view>
      </view>

      <!-- 时段筛选：早晚是选票最常见的诉求 -->
      <scroll-view scroll-x class="filter-scroll" :show-scrollbar="false">
        <view class="filter-row">
          <view
            v-for="slot in TIME_SLOTS"
            :key="slot.label"
            class="filter-chip"
            :class="{ active: slot.label === timeSlot }"
            @tap="timeSlot = slot.label"
          >{{ slot.label }}</view>
          <view
            class="filter-chip"
            :class="{ active: directOnly }"
            @tap="directOnly = !directOnly"
          >只看直达</view>
          <view
            class="filter-chip"
            :class="{ active: sortMode === 'price' }"
            @tap="sortMode = sortMode === 'price' ? 'time' : 'price'"
          >{{ sortMode === 'price' ? '按价格' : '按时间' }}</view>
        </view>
      </scroll-view>

      <text class="list-hint">
        {{ loading ? '正在查' + TYPE_LABEL[type] + '班次…' : `${list.length} 个班次` }}
        · 参考价，以{{ type === 'train' ? '12306' : '携程' }}为准
        · 选中后加入行程，本站不代购
        <text v-if="!fromApi && !loading" class="offline-hint">（当前为离线候选）</text>
      </text>

      <!-- 出发地还没定：查不了票。给一条明确出路，而不是一个空白列表。 -->
      <view v-if="!fromCity" class="empty-card">
        <text class="empty-title">还不知道从哪出发</text>
        <text class="empty-text">车票与机票都要看出发地。回对话页说一下「从XX出发」，这里就能查了。</text>
        <view class="empty-btn" @tap="goBack">回对话页补出发地</view>
      </view>

      <!--
        该线路没有候选班次：降级为「直接去官网查」。
        这条路径必须始终可用 —— 候选库没收录的线路（绝大多数）全靠它，
        否则数据源未就绪就等于整块功能不可用（见 产品项目文档 5.7.1 的降级策略）。
      -->
      <view v-else-if="!loading && list.length === 0" class="empty-card">
        <text class="empty-title">这条线路暂时没有候选{{ TYPE_LABEL[type] }}班次</text>
        <text class="empty-text">{{ routeText }} · 可直接去{{ type === 'train' ? '12306' : '携程' }}查询最新班次与票价</text>
        <view class="empty-btn primary" @tap="openOfficial(segment)">去{{ type === 'train' ? '12306' : '携程' }}查询 →</view>
      </view>

      <view class="ticket-list">
        <view
          v-for="t in list"
          :key="t.id"
          class="ticket-card"
          :class="{ picked: isPicked(t) }"
          @tap="pick(t)"
        >
          <view class="ticket-head">
            <text class="ticket-no">{{ t.ticketNo }}</text>
            <text class="ticket-carrier">{{ t.carrier }}</text>
            <view class="radio" :class="{ on: isPicked(t) }" />
          </view>

          <view class="time-row">
            <view class="time-block">
              <text class="time">{{ t.departTime }}</text>
              <text class="station">{{ t.fromStation }}</text>
            </view>
            <view class="time-mid">
              <text class="duration">{{ formatDuration(t.durationMin) }}</text>
              <view class="time-line" />
              <text class="stop-tag">{{ t.stops > 0 ? `经停${t.stops}站` : '直达' }}</text>
            </view>
            <view class="time-block right">
              <text class="time">{{ t.arriveTime }}</text>
              <text class="station">{{ t.toStation }}</text>
            </view>
          </view>

          <view v-if="t.tags.length" class="tag-row">
            <text v-for="tag in t.tags" :key="tag" class="tag">{{ tag }}</text>
          </view>

          <view class="price-row">
            <text class="seat-class">{{ t.seatClass }}</text>
            <text class="ticket-price">{{ formatTicketPrice(t.price) }}</text>
            <!-- 参考价：不做「有票/可订」承诺，本站没有余票与舱位库存 -->
            <text class="price-note">参考价</text>
            <text class="buy-btn" @tap.stop="onBuy(t)">去{{ t.transportType === 'train' ? '12306' : '携程' }} →</text>
          </view>
        </view>
      </view>

      <view style="height: 260rpx" />
    </scroll-view>

    <!-- 底部：暂不选择 + 加入行程 -->
    <view class="footer safe-bottom">
      <button class="btn-skip" @tap="confirm(true)">暂不选择</button>
      <button class="btn-ok" :disabled="!hasPicked" @tap="confirm(false)">{{ okLabel }}</button>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 订票页（火车票 / 飞机票）。
 *
 * 位置：AI 对话页的浮出工具条「🚂 查车票」→ 本页 → 回到原页。
 *   与「选酒店」是同一层的入口（后端 ready.transport 已预留该标记）。
 *
 * 一条硬约束：交易不在站内完成。
 *   价格全部是**参考价**，页面上必须带「以 12306 / 携程为准」，
 *   禁止出现「有票」「可订」这类承诺 —— 我们只有候选库，没有余票与舱位。
 *   「去 12306 / 携程」是跳外部 H5（utils/deeplink.ts），本站不做下单、不代购，
 *   也绝不收集 12306 账号、证件号等购票凭证（本页一个相关输入框都没有）。
 *
 * 数据来源：utils/tickets.ts 的 fetchTicketOptions —— 优先后端 /ticket/search
 *   （候选库可运营维护），接口不可用或线路未收录时自动退到本地候选表；
 *   两者都查不到时降级为「去官网查」按钮（数据源未就绪也能用）。
 *
 * 往返：去程与返程各查一次、各选一条，互不影响 —— 也可以只选一段。
 *   两段用同一个 ticketStore 槽位保存，确认后一起落库（trip_ticket 两个方向各一条）。
 *
 * 选中结果写入 tripStore.selectedTickets：
 *   · 行程已保存（有 id）时立刻调 /ticket/select 落库，杀进程再打开行程仍能看到；
 *   · 行程还没保存就先本地暂存，store.saveTrip 保存成功后会自动补写一次。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon/AppIcon.vue'
import { useTripStore } from '@/store/trip'
import {
  fetchTicketOptions,
  formatDuration,
  formatTicketPrice,
  sortByPrice,
  TICKET_TYPE_LABEL,
  TICKET_DIRECTION_LABEL,
  type TicketOption,
  type TicketType,
  type TicketDirection
} from '@/utils/tickets'
import { openTicketPurchase } from '@/utils/deeplink'
import { showToast } from '@/utils/feedback'

const tripStore = useTripStore()

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const navHeight = statusBarHeight + 48

const TYPE_LABEL = TICKET_TYPE_LABEL

const TYPES: Array<{ label: string; value: TicketType }> = [
  { label: '🚂 火车', value: 'train' },
  { label: '✈️ 飞机', value: 'flight' }
]

const MODES: Array<{ label: string; round: boolean }> = [
  { label: '往返', round: true },
  { label: '单程', round: false }
]

const DIRECTIONS: Array<{ label: string; value: TicketDirection }> = [
  { label: TICKET_DIRECTION_LABEL.outbound, value: 'outbound' },
  { label: TICKET_DIRECTION_LABEL.return, value: 'return' }
]

/** 时段筛选：按出发时刻分档（早晚是选票最常见的诉求） */
const TIME_SLOTS = [
  { label: '不限', from: 0, to: 24 },
  { label: '上午', from: 5, to: 12 },
  { label: '下午', from: 12, to: 18 },
  { label: '晚上', from: 18, to: 24 }
]

/** 目的地城市 */
const city = ref('')
/** 出发地城市（查票的必需条件） */
const fromCity = ref('')
const startDate = ref('')
const endDate = ref('')
/**
 * 从哪来的：'chat'（对话页工具条）/ 'detail'（行程详情更换票务）/ 空。
 * 前两者确认后应该 navigateBack 回原页，而不是再 push 一个对话页。
 */
const fromPage = ref('')

const type = ref<TicketType>('train')
const roundTrip = ref(true)
const segment = ref<TicketDirection>('outbound')

const loading = ref(false)
/** 数据来源：接口 or 本地兜底 —— 兜底时页面提示一句 */
const fromApi = ref(true)
const outboundList = ref<TicketOption[]>([])
const returnList = ref<TicketOption[]>([])
const pickedOutbound = ref<TicketOption | null>(null)
const pickedReturn = ref<TicketOption | null>(null)

const timeSlot = ref('不限')
const directOnly = ref(false)
const sortMode = ref<'time' | 'price'>('time')

/** 当前段（去程/返程，单程时恒为去程） */
const activeSegment = computed<TicketDirection>(() => (roundTrip.value ? segment.value : 'outbound'))

const routeText = computed(() => {
  const from = fromCity.value || '出发地'
  const to = city.value || '目的地'
  const date = activeSegment.value === 'return' ? endDate.value : startDate.value
  return `${from} → ${to}${date ? ` · ${date}` : ''}`
})

const activeList = computed(() =>
  activeSegment.value === 'return' ? returnList.value : outboundList.value
)

/** 展示列表：在候选基础上做时段 + 直达过滤，并按所选维度排序 */
const list = computed(() => {
  const slot = TIME_SLOTS.find((s) => s.label === timeSlot.value) || TIME_SLOTS[0]
  const filtered = activeList.value.filter((t) => {
    const hour = Number((t.departTime || '00:00').split(':')[0])
    if (hour < slot.from || hour >= slot.to) return false
    if (directOnly.value && t.stops > 0) return false
    return true
  })
  return sortMode.value === 'price' ? sortByPrice(filtered) : filtered
})

const hasPicked = computed(() =>
  roundTrip.value ? !!pickedOutbound.value || !!pickedReturn.value : !!pickedOutbound.value
)

/** 往返且两段都选了才点明「往返」，避免按钮文案与实际写入的内容不符 */
const okLabel = computed(() =>
  roundTrip.value && pickedOutbound.value && pickedReturn.value ? '加入行程（往返）' : '加入行程'
)

onLoad(async (query) => {
  // 城市优先取 query（刷新/直达也能用），其次取上一页组装好的行程。
  // 注意参数命名：出发地用 dep，from 留给「从哪个页面进来的」——
  // 与 pages/plan/hotel.vue 的 from=chat|detail 约定保持一致，避免两者撞名。
  city.value = query?.city ? decodeURIComponent(String(query.city)) : tripStore.currentTrip?.toCity || ''
  fromCity.value = query?.dep ? decodeURIComponent(String(query.dep)) : tripStore.currentTrip?.fromCity || ''
  startDate.value = query?.start ? decodeURIComponent(String(query.start)) : tripStore.currentTrip?.startDate || ''
  endDate.value = query?.end ? decodeURIComponent(String(query.end)) : tripStore.currentTrip?.endDate || ''
  fromPage.value = query?.from ? String(query.from) : ''
  const qType = query?.type ? String(query.type) : ''
  if (qType === 'train' || qType === 'flight') type.value = qType
  if (query?.mode === 'oneway') roundTrip.value = false
  // 行程详情页点的是「返程」那一行时直接落到返程页签，省一次点击
  if (String(query?.seg) === 'return') {
    roundTrip.value = true
    segment.value = 'return'
  }

  // 从行程详情/对话页再进来时回显上次的选择
  pickedOutbound.value = tripStore.selectedTickets.outbound
  pickedReturn.value = tripStore.selectedTickets.return

  await load()
})

/**
 * 拉候选。
 *
 * 往返时查两次（去程与返程方向相反、日期不同），单程只查去程。
 * 没有出发地就不查：没有方向的班次列表对用户毫无意义，
 * 页面会显示「回对话页补出发地」的明确出路。
 */
async function load() {
  if (!fromCity.value || !city.value) {
    outboundList.value = []
    returnList.value = []
    return
  }
  loading.value = true
  try {
    const outbound = await fetchTicketOptions({
      type: type.value,
      from: fromCity.value,
      to: city.value,
      date: startDate.value
    })
    outboundList.value = outbound.list
    fromApi.value = outbound.fromApi

    if (roundTrip.value) {
      const back = await fetchTicketOptions({
        type: type.value,
        from: city.value,
        to: fromCity.value,
        date: endDate.value
      })
      returnList.value = back.list
      // 两段有一段落到了本地兜底就整体提示「离线候选」，避免用户以为只有一半是准的
      fromApi.value = fromApi.value && back.fromApi
    } else {
      returnList.value = []
    }
  } finally {
    loading.value = false
  }
}

async function pickType(next: TicketType) {
  if (type.value === next) return
  type.value = next
  // 换票种等于换了一批班次：已选的两段都不再对应当前列表，清掉避免「选了火车却存着机票」
  pickedOutbound.value = null
  pickedReturn.value = null
  await load()
}

async function pickMode(round: boolean) {
  if (roundTrip.value === round) return
  roundTrip.value = round
  if (!round) segment.value = 'outbound'
  await load()
}

function pick(t: TicketOption) {
  if (activeSegment.value === 'return') {
    pickedReturn.value = pickedReturn.value?.id === t.id ? null : t
  } else {
    pickedOutbound.value = pickedOutbound.value?.id === t.id ? null : t
  }
}

function isPicked(t: TicketOption): boolean {
  return activeSegment.value === 'return'
    ? pickedReturn.value?.id === t.id
    : pickedOutbound.value?.id === t.id
}

/** 段标签上的小字：这一段选了什么，用户一眼知道这步做完了 */
function pickedLabel(direction: TicketDirection): string {
  const picked = direction === 'return' ? pickedReturn.value : pickedOutbound.value
  return picked ? `已选 ${picked.ticketNo}` : ''
}

/** 购票：后端给的深链优先，没有就按同一套模板现场拼（见 utils/deeplink.ts） */
function onBuy(t: TicketOption) {
  openTicketPurchase(t.purchaseUrl, {
    type: t.transportType,
    fromStation: t.fromStation,
    toStation: t.toStation,
    fromCity: t.fromCity || fromCity.value,
    toCity: t.toCity || city.value,
    date: activeSegment.value === 'return' ? endDate.value : startDate.value,
    ticketNo: t.ticketNo
  })
}

/** 线路未收录时的降级：直接跳官网查询页（站名 + 日期都带上） */
function openOfficial(direction: TicketDirection) {
  const back = direction === 'return'
  openTicketPurchase(undefined, {
    type: type.value,
    fromCity: back ? city.value : fromCity.value,
    toCity: back ? fromCity.value : city.value,
    date: back ? endDate.value : startDate.value
  })
}

/**
 * @param skip true = 暂不选择
 *
 * 「暂不选择」的语义是「这次不新建选择」，不是「清掉已有票务」——
 * 与酒店页同款判断：用户从行程详情点「更换票务」进来再点暂不选择，
 * 不该把已经存好的票清空（否则详情页卡片消失、数据库里记录还在，两处对不上）。
 */
async function confirm(skip: boolean) {
  if (skip) {
    const hadSaved =
      !!tripStore.selectedTickets.outbound || !!tripStore.selectedTickets.return
    if (!hadSaved) {
      tripStore.setSelectedTicket('outbound', null)
      tripStore.setSelectedTicket('return', null)
    }
    gotoBack()
    return
  }

  const outbound = pickedOutbound.value
  const back = roundTrip.value ? pickedReturn.value : null
  if (!outbound && !back) {
    showToast({ title: '先选一个班次', icon: 'none' })
    return
  }

  const prevOutbound = tripStore.selectedTickets.outbound
  const prevReturn = tripStore.selectedTickets.return

  tripStore.setSelectedTicket('outbound', outbound)
  // 单程时把返程显式清掉：用户切到单程就是不要返程票了
  tripStore.setSelectedTicket('return', back)

  // 行程已保存 → 立刻落库（详情页靠它显示「怎么去、怎么回」）
  const tripId = tripStore.currentTrip?.id
  if (tripId) {
    try {
      await tripStore.persistSelectedTickets(tripId)
      // 本次被取消的方向要显式删库：否则卡片不显示、库里却还留着旧票，换个入口又会「复活」
      if (prevOutbound && !outbound) await tripStore.clearSelectedTicket('outbound', tripId)
      if (prevReturn && !back) await tripStore.clearSelectedTicket('return', tripId)
    } catch (e) {
      // 落库失败不影响用户继续走：选中状态已在 store 里
      console.warn('[ticket] 票务落库失败（本地仍保留选择）:', e)
    }
  }
  gotoBack()
}

function gotoBack() {
  // 从对话页/行程详情进来的，确认后回原页（navigateBack）；
  // 没有上一页可回（如直接刷新本页）时才 push 对话页。
  uni.navigateBack({
    fail: () => uni.navigateTo({ url: '/pages/ai/chat' })
  })
}

function goBack() {
  uni.navigateBack({
    fail: () => uni.navigateTo({ url: '/pages/ai/chat' })
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

/* ── 票种 / 单程往返 / 去返程分段 ── */
.seg-row {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}

.seg {
  flex: 1;
  text-align: center;
  padding: 18rpx 0;
  border-radius: 20rpx;
  background: var(--bg-input);
  color: var(--text-secondary);
  font-size: var(--fs-meta);

  &.active {
    background: var(--brand-grad);
    color: var(--on-brand);
    font-weight: 600;
  }
}

.seg-picked {
  display: block;
  font-size: 20rpx;
  opacity: 0.85;
  margin-top: 2rpx;
}

/* ── 筛选 ── */
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

.offline-hint {
  color: var(--text-tertiary);
}

/* ── 空态 / 降级 ── */
.empty-card {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding: 36rpx 32rpx;
  margin-bottom: 24rpx;
  border-radius: 24rpx;
  background: var(--bg-card);
  border: 1rpx solid var(--border);
}

.empty-title {
  font-size: var(--fs-title);
  font-weight: 700;
  color: var(--text-main);
}

.empty-text {
  font-size: var(--fs-meta);
  color: var(--text-secondary);
  line-height: 1.6;
}

.empty-btn {
  align-self: flex-start;
  padding: 14rpx 32rpx;
  border-radius: 999rpx;
  border: 1rpx solid var(--brand);
  color: var(--brand-deep);
  font-size: var(--fs-caption);
  font-weight: 600;

  &.primary {
    background: var(--brand-grad);
    border-color: transparent;
    color: var(--on-brand);
  }
}

/* ── 班次卡片 ── */
.ticket-card {
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

.ticket-head {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.ticket-no {
  font-size: var(--fs-title);
  font-weight: 700;
  color: var(--text-main);
}

.ticket-carrier {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

/* 单选圆点：选中后填充品牌色 + 白色内点 */
.radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 3rpx solid var(--border-strong);
  flex-shrink: 0;

  &.on {
    border-color: var(--brand);
    background: var(--brand);
    box-shadow: inset 0 0 0 7rpx var(--bg-card);
  }
}

.time-row {
  display: flex;
  align-items: flex-start;
  margin-top: 20rpx;
}

.time-block {
  min-width: 0;

  &.right {
    text-align: right;
  }
}

.time {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.2;
}

.station {
  display: block;
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  margin-top: 4rpx;
}

.time-mid {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 20rpx;
  margin-top: 6rpx;
}

.duration {
  font-size: var(--fs-caption);
  color: var(--text-secondary);
}

.time-line {
  width: 100%;
  height: 2rpx;
  margin: 8rpx 0;
  background: var(--border-strong);
}

.stop-tag {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 16rpx;
}

.tag {
  font-size: var(--fs-caption);
  color: var(--text-secondary);
  background: var(--bg-muted);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

/* 价格行：参考价 + 官网入口（跳外部 H5，不在站内下单） */
.price-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 16rpx;
}

.seat-class {
  font-size: var(--fs-caption);
  color: var(--brand-deep);
  background: var(--bg-input);
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
}

.ticket-price {
  font-size: var(--fs-body);
  font-weight: 700;
  color: var(--brand-ink);
}

.price-note {
  font-size: var(--fs-caption);
  color: var(--text-tertiary);
  margin-right: auto;
}

.buy-btn {
  flex-shrink: 0;
  padding: 8rpx 22rpx;
  border-radius: 999rpx;
  border: 1rpx solid var(--brand);
  color: var(--brand-deep);
  font-size: var(--fs-caption);
  font-weight: 600;
  background: var(--bg-card);
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
