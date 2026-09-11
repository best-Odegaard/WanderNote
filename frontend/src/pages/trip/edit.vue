<template>
  <view class="page">
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

    <button class="btn-primary" :loading="saving" @tap="handleSave">保存修改</button>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useTripStore } from '@/store/trip'
import type { TripPlan } from '@/api/trip'

const tripStore = useTripStore()
const saving = ref(false)
const tripId = ref<string | number>('')

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
  // 无 id 时为新建行程（空白表单）；有 id 时拉取真实行程填充
  if (!tripId.value) return
  try {
    const trip = await tripStore.getTripDetail(tripId.value)
    Object.assign(form, trip)
  } catch {
    uni.showToast({ title: '行程加载失败', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 800)
  }
})

/** 预算输入过滤：仅保留非负数字，避免出现负数或非法字符 */
function onBudgetInput(e: any) {
  const cleaned = e.detail.value.replace(/\D/g, '')
  form.budget = cleaned === '' ? 0 : Number(cleaned)
}

async function handleSave() {
  // 预算兜底：防止为负数或非法值
  if (typeof form.budget !== 'number' || !Number.isFinite(form.budget) || form.budget < 0) {
    form.budget = 0
  }
  saving.value = true
  try {
    if (tripId.value) {
      await tripStore.saveTrip(form as TripPlan)
    }
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  } catch {
    uni.showToast({ title: '保存失败，请重试', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
  padding: 24rpx 32rpx;
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
  font-size: 26rpx;
  color: var(--text-secondary);
  margin-bottom: 8rpx;
  display: block;
}

.input {
  height: 80rpx;
  padding: 0 20rpx;
  background: var(--bg-input);
  border-radius: 12rpx;
  font-size: 28rpx;
}
</style>
