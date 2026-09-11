<template>
  <div class="page-card">
    <div class="table-toolbar">
      <span class="text-muted">共 {{ list.length }} 条</span>
      <el-button type="primary" @click="openForm()">新增 Banner</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="图片" width="120">
        <template #default="{ row }">
          <el-image v-if="row.imageUrl" :src="row.imageUrl" fit="cover" class="cover" />
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="subtitle" label="副标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="emoji" label="Emoji" width="80" />
      <el-table-column prop="linkUrl" label="跳转链接" min-width="180" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="70" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button
            v-if="row.status === 1"
            link
            type="warning"
            @click="toggleStatus(row, 0)"
          >禁用</el-button>
          <el-button v-else link type="success" @click="toggleStatus(row, 1)">启用</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="form.visible" :title="form.id ? '编辑 Banner' : '新增 Banner'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="Banner 标题" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" placeholder="副标题" />
        </el-form-item>
        <el-form-item label="Emoji">
          <el-input v-model="form.emoji" placeholder="如：🏞️" style="width: 140px" />
        </el-form-item>
        <el-form-item label="图片URL">
          <el-input v-model="form.imageUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="跳转链接">
          <el-input v-model="form.linkUrl" placeholder="https://...（可空）" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getBannerList,
  addBanner,
  updateBanner,
  deleteBanner,
  updateBannerStatus,
  type BannerSaveParams
} from '@/api/home'
import type { BannerItem } from '@/types'

const loading = ref(false)
const list = ref<BannerItem[]>([])
const form = reactive<BannerSaveParams & { visible: boolean; saving: boolean }>({
  visible: false,
  saving: false,
  sortOrder: 0,
  status: 1
})

async function load() {
  loading.value = true
  try {
    list.value = await getBannerList()
  } finally {
    loading.value = false
  }
}

function openForm(row?: BannerItem) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      title: row.title || '',
      subtitle: row.subtitle || '',
      emoji: row.emoji || '',
      imageUrl: row.imageUrl || '',
      linkUrl: row.linkUrl || '',
      sortOrder: row.sortOrder ?? 0,
      status: row.status ?? 1
    })
  } else {
    Object.assign(form, {
      id: undefined,
      title: '',
      subtitle: '',
      emoji: '',
      imageUrl: '',
      linkUrl: '',
      sortOrder: 0,
      status: 1
    })
  }
  form.visible = true
}

async function save() {
  form.saving = true
  try {
    if (form.id) {
      await updateBanner({ ...form })
      ElMessage.success('已保存')
    } else {
      await addBanner({ ...form })
      ElMessage.success('已新增')
    }
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}

function toggleStatus(row: BannerItem, status: number) {
  const action = status === 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确定${action}Banner「${row.title || row.id}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await updateBannerStatus(row.id, status)
      ElMessage.success(`已${action}`)
      load()
    })
    .catch(() => {})
}

function remove(row: BannerItem) {
  ElMessageBox.confirm(`确定删除Banner「${row.title || row.id}」？`, '删除确认', { type: 'error' })
    .then(async () => {
      await deleteBanner(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.cover {
  width: 96px;
  height: 48px;
  border-radius: 4px;
}
</style>
