<template>
  <div class="trend-chart">
    <svg :viewBox="`0 0 ${width} ${height}`" class="chart-svg">
      <!-- 网格线 -->
      <line
        v-for="i in 4"
        :key="i"
        :x1="padding"
        :x2="width - padding"
        :y1="gridY(i)"
        :y2="gridY(i)"
        class="grid-line"
      />
      <!-- 柱状图（每组两根柱：用户/游记） -->
      <g v-for="(item, idx) in data" :key="item.date">
        <rect
          :x="barX(idx) - barW"
          :y="barY(item.userCount)"
          :width="barW"
          :height="Math.max(barH(item.userCount), 1)"
          rx="2"
          fill="#409eff"
          :class="{ 'bar-hidden': item.userCount === 0 }"
        />
        <rect
          :x="barX(idx)"
          :y="barY(item.journalCount)"
          :width="barW"
          :height="Math.max(barH(item.journalCount), 1)"
          rx="2"
          fill="#67c23a"
          :class="{ 'bar-hidden': item.journalCount === 0 }"
        />
        <text :x="barX(idx) + barW / 2" :y="height - 6" text-anchor="middle" class="axis-label">
          {{ item.date }}
        </text>
        <text :x="barX(idx) + barW / 2" :y="barY(Math.max(item.userCount, item.journalCount)) - 6" text-anchor="middle" class="value-label">
          {{ Math.max(item.userCount, item.journalCount) || '' }}
        </text>
      </g>
    </svg>
    <div class="legend">
      <span class="legend-item"><i class="dot" style="background: #409eff"></i>新增用户</span>
      <span class="legend-item"><i class="dot" style="background: #67c23a"></i>新增游记</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TrendItem } from '@/types'

const props = defineProps<{ data: TrendItem[] }>()

const width = 760
const height = 300
const padding = 32
const chartH = computed(() => height - padding - 26)
const maxValue = computed(() =>
  Math.max(1, ...props.data.map((d) => Math.max(d.userCount, d.journalCount)))
)
const barW = 14
const groupW = computed(() => (width - padding * 2) / Math.max(props.data.length, 1))
const barX = (idx: number) => padding + idx * groupW.value + groupW.value / 2 - barW
const gridY = (i: number) => padding + (chartH.value / 4) * i
const barH = (v: number) => (v / maxValue.value) * chartH.value
const barY = (v: number) => padding + chartH.value - barH(v)
</script>

<style scoped>
.chart-svg {
  width: 100%;
  height: auto;
  display: block;
}

.grid-line {
  stroke: #e8e8e8;
  stroke-width: 1;
}

.axis-label {
  font-size: 12px;
  fill: #909399;
}

.value-label {
  font-size: 10px;
  fill: #606266;
}

.bar-hidden {
  fill-opacity: 0.15;
}

.legend {
  display: flex;
  gap: 20px;
  justify-content: center;
  margin-top: 8px;
}

.legend-item {
  font-size: 13px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  display: inline-block;
}
</style>
