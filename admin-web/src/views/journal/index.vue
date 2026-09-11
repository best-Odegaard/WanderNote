<template>
  <div class="page-card">
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="游记标题"
        clearable
        style="width: 240px"
        @keyup.enter="load"
        @clear="load"
      />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
        <el-option label="正常" :value="1" />
        <el-option label="已下架" :value="0" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
      <el-table-column label="作者" width="130">
        <template #default="{ row }">{{ row.nickname || row.username || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '正常' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="likeCount" label="点赞" width="80" />
      <el-table-column prop="collectCount" label="收藏" width="80" />
      <el-table-column prop="commentCount" label="评论" width="80" />
      <el-table-column prop="createTime" label="发布时间" width="160" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button
            v-if="row.status === 1"
            link
            type="warning"
            @click="toggleStatus(row, 0)"
          >下架</el-button>
          <el-button
            v-else
            link
            type="success"
            @click="toggleStatus(row, 1)"
          >恢复</el-button>
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
    <el-drawer v-model="drawer.visible" title="游记详情" size="560px">
      <div v-if="drawer.item" v-loading="drawer.loading" class="detail">
        <h3 class="detail-title">{{ drawer.item.title }}</h3>
        <div class="detail-meta">
          <span>作者：{{ drawer.item.nickname || drawer.item.username || '-' }}</span>
          <span>发布于：{{ drawer.item.createTime }}</span>
        </div>
        <div v-if="drawer.item.location" class="text-muted" style="font-size: 13px; margin-bottom: 10px">
          位置：{{ drawer.item.location }}
        </div>
        <div class="detail-content">{{ drawer.item.content }}</div>

        <div class="detail-label">评论（{{ drawer.item.comments.length }}）</div>
        <div v-if="drawer.item.comments.length === 0" class="text-muted">暂无评论</div>
        <div v-for="c in drawer.item.comments" :key="c.id" class="comment-item">
          <div class="comment-head">
            <span class="comment-user">{{ c.nickname || c.username || '匿名' }}</span>
            <span v-if="c.parentUsername" class="text-muted">回复 @{{ c.parentUsername }}</span>
            <span class="text-muted" style="font-size: 12px">{{ c.createTime }}</span>
          </div>
          <div class="comment-content" :class="{ deleted: c.status === 2 }">
            {{ c.status === 2 ? '（评论已删除）' : c.content }}
          </div>
          <el-button
            v-if="c.status !== 2"
            link
            type="danger"
            size="small"
            @click="removeComment(c.id)"
          >删除评论</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getJournalList,
  getJournalDetail,
  deleteJournal,
  updateJournalStatus,
  deleteComment
} from '@/api/journal'
import type { JournalItem, JournalDetail } from '@/types'

const loading = ref(false)
const list = ref<JournalItem[]>([])
const total = ref(0)
const query = reactive<{ pageNum: number; pageSize: number; keyword?: string; status?: number }>({
  pageNum: 1,
  pageSize: 10
})

const drawer = reactive<{ visible: boolean; item: JournalDetail | null; loading: boolean }>({
  visible: false,
  item: null,
  loading: false
})

async function load() {
  loading.value = true
  try {
    const res = await getJournalList(query)
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
  drawer.visible = true
  drawer.loading = true
  try {
    drawer.item = await getJournalDetail(id)
  } finally {
    drawer.loading = false
  }
}

function toggleStatus(row: JournalItem, status: number) {
  const action = status === 1 ? '恢复' : '下架'
  ElMessageBox.confirm(`确定${action}游记「${row.title}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await updateJournalStatus(row.id, status)
      ElMessage.success(`已${action}`)
      load()
    })
    .catch(() => {})
}

function remove(row: JournalItem) {
  ElMessageBox.confirm(`确定删除游记「${row.title}」？删除后不可恢复。`, '删除确认', { type: 'error' })
    .then(async () => {
      await deleteJournal(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

function removeComment(commentId: number) {
  ElMessageBox.confirm('确定删除该评论（连同回复）？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteComment(commentId)
      ElMessage.success('已删除')
      if (drawer.item) {
        drawer.item.comments = drawer.item.comments.filter((c) => c.id !== commentId)
      }
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

.detail-title {
  margin: 0 0 8px;
}

.detail-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #909399;
  margin-bottom: 12px;
}

.detail-content {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  margin-bottom: 16px;
}

.detail-label {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 16px 0 10px;
}

.comment-item {
  border-bottom: 1px dashed #ebeef5;
  padding: 10px 0;
}

.comment-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.comment-user {
  font-weight: 600;
  font-size: 13px;
}

.comment-content {
  font-size: 14px;
  line-height: 1.6;
}

.comment-content.deleted {
  color: #c0c4cc;
  font-style: italic;
}
</style>
