<template>
  <div>
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col v-for="card in cards" :key="card.label" :span="4">
        <div class="stat-card" :style="{ borderTopColor: card.color }">
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="16">
        <div class="page-card">
          <div class="panel-title">近 7 天新增趋势</div>
          <TrendChart :data="data?.trend || []" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="page-card">
          <div class="panel-title">反馈状态分布</div>
          <div v-for="item in statusDist" :key="item.status" class="dist-item">
            <div class="dist-head">
              <span>{{ item.label }}</span>
              <span class="dist-count">{{ item.count }}</span>
            </div>
            <el-progress
              :percentage="item.percent"
              :color="item.color"
              :stroke-width="14"
              :show-text="false"
            />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getDashboard } from '@/api/dashboard'
import type { DashboardData } from '@/types'
import TrendChart from '@/components/TrendChart.vue'

const data = ref<DashboardData>()

const cards = computed(() => [
  { label: '用户总数', value: data.value?.totalUser ?? '-', color: '#409eff' },
  { label: '今日新增用户', value: data.value?.todayUser ?? '-', color: '#67c23a' },
  { label: '游记总数', value: data.value?.totalJournal ?? '-', color: '#e6a23c' },
  { label: '评论总数', value: data.value?.totalComment ?? '-', color: '#f56c6c' },
  { label: '反馈总数', value: data.value?.totalFeedback ?? '-', color: '#909399' },
  { label: '待处理反馈', value: data.value?.pendingFeedback ?? '-', color: '#f56c6c' }
])

const STATUS_META = [
  { status: 0, label: '待处理', color: '#f56c6c' },
  { status: 1, label: '已处理', color: '#67c23a' },
  { status: 2, label: '已关闭', color: '#909399' }
]

const statusDist = computed(() => {
  const dist = data.value?.feedbackDist || []
  const total = dist.reduce((sum, d) => sum + d.count, 0) || 1
  return STATUS_META.map((meta) => {
    const item = dist.find((d) => d.status === meta.status)
    return {
      ...meta,
      count: item?.count || 0,
      percent: Math.round(((item?.count || 0) / total) * 100)
    }
  })
})

onMounted(async () => {
  data.value = await getDashboard()
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  border-top: 3px solid transparent;
  padding: 20px;
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
}

.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
}

.dist-item {
  margin-bottom: 18px;
}

.dist-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}

.dist-count {
  font-weight: 600;
}
</style>
