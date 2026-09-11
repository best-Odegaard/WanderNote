<template>
  <div class="page-card">
    <div class="table-toolbar">
      <span class="text-muted">共 {{ list.length }} 条</span>
      <el-button type="primary" @click="openForm()">新增城市</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="封面" width="110">
        <template #default="{ row }">
          <el-image v-if="row.cover" :src="row.cover" fit="cover" class="cover" />
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="城市" min-width="120" />
      <el-table-column prop="rating" label="评分" width="90" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
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
    <el-dialog v-model="form.visible" :title="form.id ? '编辑城市' : '新增城市'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="城市" required>
          <el-input v-model="form.name" placeholder="城市名称" />
        </el-form-item>
        <el-form-item label="封面URL">
          <el-input v-model="form.cover" placeholder="https://..." />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="评分">
              <el-input-number v-model="form.rating" :min="0" :max="5" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
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
  getCityList,
  addCity,
  updateCity,
  deleteCity,
  updateCityStatus,
  type CitySaveParams
} from '@/api/home'
import type { CityItem } from '@/types'

const loading = ref(false)
const list = ref<CityItem[]>([])
const form = reactive<CitySaveParams & { visible: boolean; saving: boolean }>({
  visible: false,
  saving: false,
  name: '',
  rating: 5,
  sortOrder: 0,
  status: 1
})

async function load() {
  loading.value = true
  try {
    list.value = await getCityList()
  } finally {
    loading.value = false
  }
}

function openForm(row?: CityItem) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name,
      cover: row.cover || '',
      rating: row.rating ?? 5,
      sortOrder: row.sortOrder ?? 0,
      status: row.status ?? 1
    })
  } else {
    Object.assign(form, {
      id: undefined,
      name: '',
      cover: '',
      rating: 5,
      sortOrder: 0,
      status: 1
    })
  }
  form.visible = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入城市名称')
    return
  }
  form.saving = true
  try {
    if (form.id) {
      await updateCity({ ...form })
      ElMessage.success('已保存')
    } else {
      await addCity({ ...form })
      ElMessage.success('已新增')
    }
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}

function toggleStatus(row: CityItem, status: number) {
  const action = status === 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确定${action}城市「${row.name}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await updateCityStatus(row.id, status)
      ElMessage.success(`已${action}`)
      load()
    })
    .catch(() => {})
}

function remove(row: CityItem) {
  ElMessageBox.confirm(`确定删除城市「${row.name}」？`, '删除确认', { type: 'error' })
    .then(async () => {
      await deleteCity(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.cover {
  width: 80px;
  height: 48px;
  border-radius: 4px;
}
</style>
