<template>
  <view class="budget-chart">
    <view class="chart-header">
      <text class="label">预计花费</text>
      <text class="total">{{ formatPrice(total) }}</text>
    </view>
    <view class="bars">
      <view v-for="item in items" :key="item.label" class="bar-item">
        <view class="bar-info">
          <text class="bar-label">{{ item.label }}</text>
          <text class="bar-value">{{ formatPrice(item.value) }}</text>
        </view>
        <view class="bar-track">
          <view class="bar-fill" :style="{ width: getPercent(item.value) + '%', background: item.color }" />
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatPrice } from '@/utils/format'

interface BudgetItem {
  label: string
  value: number
  color?: string
}

interface Props {
  items: BudgetItem[]
}

const props = defineProps<Props>()

const total = computed(() => props.items.reduce((sum, i) => sum + i.value, 0))

function getPercent(value: number): number {
  if (total.value === 0) return 0
  return Math.round((value / total.value) * 100)
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.budget-chart {
  background: var(--bg-card);
  border-radius: $card-radius;
  padding: 24rpx;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.label {
  font-size: 28rpx;
  color: var(--text-secondary);
}

.total {
  font-size: 36rpx;
  font-weight: 600;
  color: $primary-color;
}

.bar-item {
  margin-bottom: 20rpx;

  &:last-child { margin-bottom: 0; }
}

.bar-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.bar-label {
  font-size: 24rpx;
  color: var(--text-body);
}

.bar-value {
  font-size: 24rpx;
  color: var(--text-secondary);
}

.bar-track {
  height: 12rpx;
  background: var(--bg-page);
  border-radius: 6rpx;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 6rpx;
  transition: width 0.3s ease;
}
</style>
