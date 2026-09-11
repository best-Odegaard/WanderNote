<template>
  <div class="page-card">
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="景点名称"
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
      <div>
        <el-button @click="openImport">Excel 导入</el-button>
        <el-button type="primary" @click="openForm()">新增景点</el-button>
      </div>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="封面" width="80">
        <template #default="{ row }">
          <el-image v-if="row.cover" :src="row.cover" fit="cover" class="cover" />
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="city" label="城市" width="90" />
      <el-table-column prop="category" label="分类" width="100" />
      <el-table-column prop="rating" label="评分" width="70" />
      <el-table-column prop="price" label="价格" width="80">
        <template #default="{ row }">¥{{ row.price ?? 0 }}</template>
      </el-table-column>
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
    <el-dialog v-model="form.visible" :title="form.id ? '编辑景点' : '新增景点'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="景点名称" />
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
            <el-form-item label="评分">
              <el-input-number v-model="form.rating" :min="0" :max="5" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="价格">
              <el-input-number v-model="form.price" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="封面图">
          <el-input v-model="form.cover" placeholder="图片 URL" />
        </el-form-item>
        <el-form-item label="开放时间">
          <el-input v-model="form.openTime" placeholder="如：08:00-18:00" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="景点地址" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="图片">
          <el-input
            v-model="imagesText"
            type="textarea"
            :rows="2"
            placeholder="多个图片 URL，每行一个"
          />
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

    <!-- Excel 批量导入弹窗 -->
    <el-dialog v-model="importVisible" title="Excel 批量导入景点" width="640px">
      <div class="import-tip">
        <p>1. 点击下方「下载模板」获取标准模板（含示例行，可删除）</p>
        <p>2. 按模板填写景点数据后上传 <b>.xlsx / .xls</b> 文件</p>
        <p>3. 名称必填；评分 0~5；热门/状态填「是/否」或「1/0」；图片URL多个用 <b>|</b> 分隔</p>
        <p>4. 有问题的行会跳过并给出明细，其余正常导入</p>
      </div>

      <div class="import-actions">
        <el-button type="primary" plain @click="downloadTemplate">下载模板</el-button>
        <el-upload
          :auto-upload="false"
          :limit="1"
          accept=".xlsx,.xls"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :file-list="fileList"
          drag
          style="flex: 1"
        >
          <div class="upload-inner">
            <p>点击或拖拽 Excel 文件到此处</p>
            <p class="text-muted" style="font-size: 12px">仅支持 .xlsx / .xls</p>
          </div>
        </el-upload>
      </div>

      <div class="import-actions" style="justify-content: flex-end">
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="doImport">
          开始导入
        </el-button>
      </div>

      <!-- 导入结果 -->
      <div v-if="importResult" class="import-result">
        <el-alert
          :type="importResult.fail > 0 ? 'warning' : 'success'"
          :title="`导入完成：共 ${importResult.total} 行，成功 ${importResult.success} 条，失败 ${importResult.fail} 条`"
          :closable="false"
          show-icon
        />
        <el-table
          v-if="importResult.errors.length"
          :data="importResult.errors"
          size="small"
          max-height="220"
          border
          class="error-table"
        >
          <el-table-column prop="row" label="Excel行号" width="100" />
          <el-table-column prop="message" label="失败原因" min-width="200" />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import {
  getScenicList,
  addScenic,
  updateScenic,
  deleteScenic,
  updateScenicStatus,
  importScenicExcel,
  downloadScenicTemplate,
  type ScenicSaveParams
} from '@/api/content'
import type { ScenicItem, ImportResult } from '@/types'

const categories = ['自然风光', '历史文化', '主题乐园', '博物馆', '古镇']

const loading = ref(false)
const list = ref<ScenicItem[]>([])
const total = ref(0)

// ── Excel 批量导入状态 ──
const importVisible = ref(false)
const importFile = ref<File>()
const importing = ref(false)
const importResult = ref<ImportResult>()
const fileList = ref<UploadFile[]>([])
const query = reactive<{
  pageNum: number
  pageSize: number
  keyword?: string
  city?: string
  category?: string
  status?: number
}>({ pageNum: 1, pageSize: 10 })

const formRef = ref<FormInstance>()
const form = reactive<ScenicSaveParams & { visible: boolean; saving: boolean }>({
  visible: false,
  saving: false,
  name: '',
  rating: 5,
  price: 0,
  isHot: 0,
  sortOrder: 0,
  status: 1
})
const imagesText = ref('')

const rules: FormRules = {
  name: [{ required: true, message: '请输入景点名称', trigger: 'blur' }]
}

const isHotBool = computed({
  get: () => form.isHot === 1,
  set: (v: boolean) => (form.isHot = v ? 1 : 0)
})

async function load() {
  loading.value = true
  try {
    const res = await getScenicList(query)
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

// ── Excel 批量导入 ──

function openImport() {
  importVisible.value = true
  importFile.value = undefined
  importResult.value = undefined
  fileList.value = []
}

function handleFileChange(file: UploadFile) {
  importFile.value = file.raw
}

function handleFileRemove() {
  importFile.value = undefined
}

function downloadTemplate() {
  downloadScenicTemplate().catch(() => {})
}

async function doImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  importing.value = true
  try {
    importResult.value = await importScenicExcel(importFile.value)
    if (importResult.value.success > 0) {
      ElMessage.success(`成功导入 ${importResult.value.success} 条`)
      load()
    }
  } finally {
    importing.value = false
  }
}

function openForm(row?: ScenicItem) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name,
      cover: row.cover || '',
      city: row.city || '',
      rating: row.rating ?? 5,
      category: row.category || '',
      price: row.price ?? 0,
      openTime: row.openTime || '',
      description: row.description || '',
      address: row.address || '',
      isHot: row.isHot ?? 0,
      sortOrder: row.sortOrder ?? 0,
      status: row.status ?? 1
    })
    imagesText.value = parseImages(row.images)
  } else {
    Object.assign(form, {
      id: undefined,
      name: '',
      cover: '',
      city: '',
      rating: 5,
      category: '',
      price: 0,
      openTime: '',
      description: '',
      address: '',
      isHot: 0,
      sortOrder: 0,
      status: 1
    })
    imagesText.value = ''
  }
  form.visible = true
}

/** images 字段是 JSON 数组字符串 → 每行一个 URL */
function parseImages(images?: string): string {
  if (!images) return ''
  try {
    const arr = JSON.parse(images)
    return Array.isArray(arr) ? arr.join('\n') : ''
  } catch {
    return images
  }
}

function collectImages(): string[] {
  return imagesText.value
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
}

async function save() {
  await formRef.value?.validate()
  form.saving = true
  try {
    const payload = { ...form, images: collectImages() }
    if (form.id) {
      await updateScenic(payload)
      ElMessage.success('已保存')
    } else {
      await addScenic(payload)
      ElMessage.success('已新增')
    }
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}

function toggleStatus(row: ScenicItem, status: number) {
  const action = status === 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确定${action}「${row.name}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await updateScenicStatus(row.id, status)
      ElMessage.success(`已${action}`)
      load()
    })
    .catch(() => {})
}

function remove(row: ScenicItem) {
  ElMessageBox.confirm(`确定删除景点「${row.name}」？删除后不可恢复。`, '删除确认', { type: 'error' })
    .then(async () => {
      await deleteScenic(row.id)
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

.import-tip {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px 16px;
  font-size: 13px;
  color: #606266;
  line-height: 1.9;
  margin-bottom: 16px;
}

.import-tip p {
  margin: 0;
}

.import-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.upload-inner {
  padding: 8px 0;
}

.import-result {
  margin-top: 8px;
}

.error-table {
  margin-top: 12px;
}
</style>
