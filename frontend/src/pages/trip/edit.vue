<template>
  <view class="page">
    <!--
      无 id：这里既不是「编辑已有行程」，项目里也没有可用的新建接口。
      原实现在无 id 时跳过保存、却照样弹「保存成功」再返回 —— 用户以为存住了，实际输入全丢。
      现在如实告知「无法编辑」，并只留返回，不再给假成功（P1-21）。
    -->
    <view v-if="ready && !tripId" class="notice">
      <text class="notice-title">行程尚未保存，无法编辑</text>
      <text class="notice-sub">请先回到对话页生成并保存行程，再回来修改</text>
      <button class="btn-primary" @tap="goBack">返回</button>
    </view>

    <template v-else-if="ready">
      <view class="form card">
        <view class="form-item">
          <text class="label">行程标题</text>
          <input v-model="form.title" class="input" />
        </view>
        <view class="form-row">
          <view class="form-item half">
            <text class="label">出发地</text>
            <input v-model="form.fromCity" class="input" />
          </view>
          <view class="form-item half">
            <text class="label">目的地</text>
            <input v-model="form.toCity" class="input" />
          </view>
        </view>
        <view class="form-row">
          <view class="form-item half">
            <text class="label">天数</text>
            <input v-model.number="form.days" class="input" type="number" />
          </view>
          <view class="form-item half">
            <text class="label">人数</text>
            <input v-model.number="form.people" class="input" type="number" />
          </view>
        </view>
        <view class="form-item">
          <text class="label">预算</text>
          <input :value="String(form.budget)" class="input" type="number" @input="onBudgetInput" />
        </view>
      </view>

      <button class="btn-primary" :loading="saving" :disabled="saving" @tap="handleSave">保存修改</button>
    </template>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useTripStore } from '@/store/trip'
import type { TripPlan } from '@/api/trip'
import { showToast } from '@/utils/feedback'

const tripStore = useTripStore()
const saving = ref(false)
const tripId = ref<string | number>('')
/** 页面参数读完前不渲染：否则无 id 进来会先闪一下「无法编辑」，随后又变回表单 */
const ready = ref(false)

const form = reactive<Partial<TripPlan>>({
  title: '',
  fromCity: '',
  toCity: '',
  days: 3,
  people: 2,
  budget: 3000
})

onMounted(async () => {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as { options?: { id?: string } }
  tripId.value = page.options?.id || ''
  // 无 id：没有可编辑的对象（详情页齿轮已对精选行程隐藏，这里是兜底入口），直接进「无法编辑」态
  if (!tripId.value) {
    ready.value = true
    return
  }
  // 有 id 时拉取真实行程填充
  try {
    const trip = await tripStore.getTripDetail(tripId.value)
    Object.assign(form, trip)
  } catch {
    showToast({ title: '行程加载失败', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 800)
  }
  ready.value = true
})

/** 预算输入过滤：仅保留非负数字，避免出现负数或非法字符 */
function onBudgetInput(e: any) {
  const cleaned = e.detail.value.replace(/\D/g, '')
  form.budget = cleaned === '' ? 0 : Number(cleaned)
}

/** 天数/人数必须是 ≥1 的整数（P2-14）；0、空、小数一律不放行 */
function isPositiveInt(v: unknown): boolean {
  const n = Number(v)
  return Number.isInteger(n) && n >= 1
}

/**
 * 让 dayPlans 与天数输入保持一致（P1-22）。
 *
 * 天数在本页是独立输入框，而列表卡片、详情页摘要与天签读的都是 dayPlans：
 * 只改天数不改排期就会留下 trip.days=5 却只有 3 天安排的数据，两边数字永远对不上。
 * 保存前按新天数补齐空白天 / 裁剪多出的天；先 slice 复制，避免原地改 store 里那条行程的数组。
 */
function syncDayPlans(days: number) {
  const plans = (form.dayPlans || []).slice(0, days)
  for (let i = plans.length; i < days; i++) {
    plans.push({ day: i + 1, title: `第${i + 1}天`, schedules: [] })
  }
  form.dayPlans = plans
}

async function handleSave() {
  // 无 id 时既不能更新也不能新建：必须如实拒绝，而不是提示「保存成功」（P1-21）
  if (!tripId.value) {
    showToast({ title: '行程尚未保存，无法编辑', icon: 'none' })
    return
  }
  const days = Number(form.days)
  const people = Number(form.people)
  if (!isPositiveInt(days)) {
    showToast({ title: '天数需为不小于 1 的整数', icon: 'none' })
    return
  }
  if (!isPositiveInt(people)) {
    showToast({ title: '人数需为不小于 1 的整数', icon: 'none' })
    return
  }
  // 校验通过后统一成整数，避免 "3.0" / 字符串形态落到后端
  form.days = days
  form.people = people
  // 预算兜底：防止为负数或非法值
  if (typeof form.budget !== 'number' || !Number.isFinite(form.budget) || form.budget < 0) {
    form.budget = 0
  }
  // 天数与排期对齐后再保存（P1-22）
  syncDayPlans(days)

  saving.value = true
  try {
    await tripStore.saveTrip(form as TripPlan)
    showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  } catch {
    showToast({ title: '保存失败，请重试', icon: 'none' })
  } finally {
    saving.value = false
  }
}

/** 返回：没有上一页（外链/冷启动直达本页）时回行程列表，避免停在空白页 */
function goBack() {
  uni.navigateBack({
    fail: () => uni.switchTab({ url: '/pages/trip/index' })
  })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
  padding: 24rpx 32rpx;
}

/* 无 id 时的说明卡：不展示空表单，避免看起来「只是还没填」 */
.notice {
  margin-top: 120rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}

.notice-title {
  font-size: var(--fs-title);
  font-weight: 700;
  color: var(--text-main);
}

.notice-sub {
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
  margin-bottom: 32rpx;
}

.form-item {
  margin-bottom: 24rpx;
  &.half { flex: 1; }
}

.form-row {
  display: flex;
  gap: 20rpx;
}

.label {
  font-size: var(--fs-body);
  color: var(--text-secondary);
  margin-bottom: 8rpx;
  display: block;
}

.input {
  height: 80rpx;
  padding: 0 20rpx;
  background: var(--bg-input);
  border-radius: 12rpx;
  font-size: var(--fs-body);
}
</style>
