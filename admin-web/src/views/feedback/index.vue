<template>
  <div class="page-card">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="query.status" placeholder="处理状态" clearable style="width: 140px">
        <el-option label="待处理" :value="0" />
        <el-option label="已处理" :value="1" />
        <el-option label="已关闭" :value="2" />
      </el-select>
      <el-select v-model="query.type" placeholder="反馈类型" clearable style="width: 140px">
        <el-option v-for="t in types" :key="t" :label="t" :value="t" />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="内容 / 联系方式"
        clearable
        style="width: 220px"
        @keyup.enter="load"
        @clear="load"
      />
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="用户" width="140">
        <template #default="{ row }">
          <div>{{ row.nickname || row.username || '-' }}</div>
          <div class="text-muted" style="font-size: 12px">@{{ row.username || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="类型" width="110">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
      <el-table-column prop="contact" label="联系方式" width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.contact || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="提交时间" width="160" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button
            v-if="row.status === 0"
            link
            type="success"
            @click="openHandle(row)"
          >处理</el-button>
          <el-button v-if="row.status === 0" link type="warning" @click="close(row)">关闭</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.pageSize"
      :current-page="query.pageNum"
      @current-change="onPageChange"
    />

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawer.visible" title="反馈详情" size="480px">
      <div v-if="drawer.item" class="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="用户">
            {{ drawer.item.nickname || drawer.item.username || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag size="small" effect="plain">{{ drawer.item.type }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="联系方式">{{ drawer.item.contact || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ drawer.item.createTime }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(drawer.item.status)" size="small">
              {{ statusText(drawer.item.status) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-block">
          <div class="detail-label">反馈内容</div>
          <div class="detail-content">{{ drawer.item.content }}</div>
        </div>

        <div v-if="drawer.item.images && drawer.item.images.length" class="detail-block">
          <div class="detail-label">图片</div>
          <div class="img-list">
            <el-image
              v-for="(img, i) in drawer.item.images"
              :key="img"
              :src="img"
              :preview-src-list="drawer.item.images"
              :initial-index="i"
              fit="cover"
              class="img-item"
              preview-teleported
            />
          </div>
        </div>

        <div v-if="drawer.item.reply" class="detail-block">
          <div class="detail-label">管理员回复</div>
          <div class="reply-box">{{ drawer.item.reply }}</div>
        </div>

        <!-- 处理表单（仅待处理） -->
        <div v-if="drawer.item.status === 0" class="detail-block">
          <div class="detail-label">回复并处理</div>
          <el-input
            v-model="drawer.reply"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            placeholder="输入回复内容，提交后反馈标记为已处理"
          />
          <div class="handle-actions">
            <el-button type="primary" :loading="handling" @click="submitHandle">提交回复</el-button>
            <el-button type="warning" :loading="handling" @click="submitClose">直接关闭</el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFeedbackList, getFeedbackDetail, handleFeedback, closeFeedback, deleteFeedback } from '@/api/feedback'
import type { FeedbackItem } from '@/types'

const types = ['功能建议', '内容纠错', '投诉举报', '其他']

const loading = ref(false)
const list = ref<FeedbackItem[]>([])
const total = ref(0)
const query = reactive<{
  pageNum: number
  pageSize: number
  status?: number
  type?: string
  keyword?: string
}>({ pageNum: 1, pageSize: 10 })

const drawer = reactive<{
  visible: boolean
  item: FeedbackItem | null
  reply: string
}>({ visible: false, item: null, reply: '' })
const handling = ref(false)

const statusText = (s: number) => (s === 0 ? '待处理' : s === 1 ? '已处理' : '已关闭')
const statusTagType = (s: number) => (s === 0 ? 'danger' : s === 1 ? 'success' : 'info')

async function load() {
  loading.value = true
  try {
    const res = await getFeedbackList(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onPageChange(page: number) {
  query.pageNum = page
  load()
}

async function openDetail(id: number) {
  drawer.item = await getFeedbackDetail(id)
  drawer.reply = ''
  drawer.visible = true
}

function openHandle(row: FeedbackItem) {
  drawer.item = { ...row }
  drawer.reply = ''
  drawer.visible = true
}

async function submitHandle() {
  if (!drawer.reply.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  if (!drawer.item) return
  handling.value = true
  try {
    await handleFeedback(drawer.item.id, drawer.reply.trim())
    ElMessage.success('已处理')
    drawer.visible = false
    load()
  } finally {
    handling.value = false
  }
}

async function submitClose() {
  if (!drawer.item) return
  handling.value = true
  try {
    await closeFeedback(drawer.item.id)
    ElMessage.success('已关闭')
    drawer.visible = false
    load()
  } finally {
    handling.value = false
  }
}

function close(row: FeedbackItem) {
  ElMessageBox.confirm('确定关闭该反馈？', '提示', { type: 'warning' })
    .then(async () => {
      await closeFeedback(row.id)
      ElMessage.success('已关闭')
      load()
    })
    .catch(() => {})
}

function remove(row: FeedbackItem) {
  ElMessageBox.confirm(`确定删除反馈 #${row.id}？删除后不可恢复。`, '删除确认', { type: 'error' })
    .then(async () => {
      await deleteFeedback(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.detail-label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}

.detail-block {
  margin-top: 20px;
}

.detail-content {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}

.reply-box {
  background: #f0f9eb;
  border-radius: 6px;
  padding: 12px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}

.img-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.img-item {
  width: 80px;
  height: 80px;
  border-radius: 6px;
}

.handle-actions {
  margin-top: 12px;
  display: flex;
  gap: 12px;
}
</style>
