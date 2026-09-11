<template>
  <div class="page-card">
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="活动标题"
        clearable
        style="width: 200px"
        @keyup.enter="load"
        @clear="load"
      />
      <el-input v-model="query.city" placeholder="城市" clearable style="width: 140px" @clear="load" />
      <el-select v-model="query.category" placeholder="分类" clearable style="width: 140px">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <div class="table-toolbar">
      <span class="text-muted">共 {{ total }} 条</span>
      <el-button type="primary" @click="openForm()">新增活动</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="封面" width="80">
        <template #default="{ row }">
          <el-image v-if="row.cover" :src="row.cover" fit="cover" class="cover" />
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="city" label="城市" width="90" />
      <el-table-column prop="category" label="分类" width="100" />
      <el-table-column prop="startTime" label="开始时间" width="150" />
      <el-table-column prop="endTime" label="结束时间" width="150" />
      <el-table-column label="热门" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.isHot === 1" type="danger" size="small">热门</el-tag>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
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

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.pageSize"
      :current-page="query.pageNum"
      @current-change="onPageChange"
    />

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="form.visible" :title="form.id ? '编辑活动' : '新增活动'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="活动标题" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="城市">
              <el-input v-model="form.city" placeholder="如：肇庆" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="form.category" placeholder="分类" clearable style="width: 100%">
                <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="开始时间">
              <el-date-picker
                v-model="form.startTime"
                type="datetime"
                placeholder="开始时间"
                value-format="YYYY-MM-DD HH:mm"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间">
              <el-date-picker
                v-model="form.endTime"
                type="datetime"
                placeholder="结束时间"
                value-format="YYYY-MM-DD HH:mm"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="封面图">
          <el-input v-model="form.cover" placeholder="图片 URL" />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="form.location" placeholder="活动地点" />
        </el-form-item>
        <el-form-item label="介绍">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="热门">
              <el-switch v-model="isHotBool" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getActivityList,
  addActivity,
  updateActivity,
  deleteActivity,
  updateActivityStatus,
  type ActivitySaveParams
} from '@/api/content'
import type { ActivityItem } from '@/types'

const categories = ['音乐节', '展览', '户外', '美食节', '文化体验']

const loading = ref(false)
const list = ref<ActivityItem[]>([])
const total = ref(0)
const query = reactive<{
  pageNum: number
  pageSize: number
  keyword?: string
  city?: string
  category?: string
  status?: number
}>({ pageNum: 1, pageSize: 10 })

const formRef = ref<FormInstance>()
const form = reactive<ActivitySaveParams & { visible: boolean; saving: boolean }>({
  visible: false,
  saving: false,
  title: '',
  isHot: 0,
  enrollCount: 0,
  sortOrder: 0,
  status: 1
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入活动标题', trigger: 'blur' }]
}

const isHotBool = computed({
  get: () => form.isHot === 1,
  set: (v: boolean) => (form.isHot = v ? 1 : 0)
})

async function load() {
  loading.value = true
  try {
    const res = await getActivityList(query)
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

function openForm(row?: ActivityItem) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      title: row.title,
      cover: row.cover || '',
      city: row.city || '',
      location: row.location || '',
      category: row.category || '',
      startTime: row.startTime || '',
      endTime: row.endTime || '',
      description: row.description || '',
      isHot: row.isHot ?? 0,
      enrollCount: row.enrollCount ?? 0,
      sortOrder: row.sortOrder ?? 0,
      status: row.status ?? 1
    })
  } else {
    Object.assign(form, {
      id: undefined,
      title: '',
      cover: '',
      city: '',
      location: '',
      category: '',
      startTime: '',
      endTime: '',
      description: '',
      isHot: 0,
      enrollCount: 0,
      sortOrder: 0,
      status: 1
    })
  }
  form.visible = true
}

async function save() {
  await formRef.value?.validate()
  form.saving = true
  try {
    if (form.id) {
      await updateActivity({ ...form })
      ElMessage.success('已保存')
    } else {
      await addActivity({ ...form })
      ElMessage.success('已新增')
    }
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}

function toggleStatus(row: ActivityItem, status: number) {
  const action = status === 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确定${action}「${row.title}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await updateActivityStatus(row.id, status)
      ElMessage.success(`已${action}`)
      load()
    })
    .catch(() => {})
}

function remove(row: ActivityItem) {
  ElMessageBox.confirm(`确定删除活动「${row.title}」？删除后不可恢复。`, '删除确认', { type: 'error' })
    .then(async () => {
      await deleteActivity(row.id)
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

.cover {
  width: 48px;
  height: 48px;
  border-radius: 4px;
}
</style>
