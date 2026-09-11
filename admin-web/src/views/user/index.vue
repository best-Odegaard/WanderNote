<template>
  <div class="page-card">
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="用户名 / 昵称 / 手机号"
        clearable
        style="width: 240px"
        @keyup.enter="load"
        @clear="load"
      />
      <el-select v-model="query.status" placeholder="账号状态" clearable style="width: 140px">
        <el-option label="正常" :value="1" />
        <el-option label="已禁用" :value="0" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="用户" min-width="160">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :src="row.avatar" :size="32">
              {{ (row.nickname || row.username || '?').slice(0, 1) }}
            </el-avatar>
            <div>
              <div>{{ row.nickname || '-' }}</div>
              <div class="text-muted" style="font-size: 12px">@{{ row.username }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="登录账号" width="130">
        <template #default="{ row }">{{ row.username }}</template>
      </el-table-column>
      <el-table-column label="登录密码" width="150">
        <template #default="{ row }">
          <el-text v-if="row.password" class="password-text">{{ row.password }}</el-text>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="130">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column prop="points" label="积分" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="160" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 1"
            link
            type="danger"
            @click="toggleStatus(row, 0)"
          >禁用</el-button>
          <el-button
            v-else
            link
            type="success"
            @click="toggleStatus(row, 1)"
          >启用</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, updateUserStatus } from '@/api/user'
import type { UserItem } from '@/types'

const loading = ref(false)
const list = ref<UserItem[]>([])
const total = ref(0)
const query = reactive<{ pageNum: number; pageSize: number; keyword?: string; status?: number }>({
  pageNum: 1,
  pageSize: 10
})

async function load() {
  loading.value = true
  try {
    const res = await getUserList(query)
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

function toggleStatus(row: UserItem, status: number) {
  const action = status === 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确定${action}用户「${row.nickname || row.username}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await updateUserStatus(row.id, status)
      ElMessage.success(`已${action}`)
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

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.password-text {
  font-family: Consolas, Monaco, monospace;
  letter-spacing: 0.5px;
}
</style>
