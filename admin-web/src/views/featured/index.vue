<template>
  <div class="page-card">
    <div class="table-toolbar">
      <span class="text-muted">共 {{ list.length }} 条</span>
      <el-button type="primary" @click="openForm()">新增精选行程</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="封面" width="110">
        <template #default="{ row }">
          <el-image v-if="row.cover" :src="row.cover" class="cover" fit="cover" />
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="subtitle" label="副标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="city" label="城市" width="90" />
      <el-table-column prop="days" label="天数" width="70" />
      <el-table-column prop="sourceUrl" label="来源链接" min-width="180" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="70" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '已上架' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button link type="primary" @click="toggleStatus(row, row.status === 1 ? 0 : 1)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="form.visible" :title="form.id ? '编辑精选行程' : '新增精选行程'" width="760px">
      <el-form :model="form" label-width="96px">
        <el-row :gutter="12">
          <el-col :span="16">
            <el-form-item label="标题">
              <el-input v-model="form.title" placeholder="如：肇庆两日悠闲游" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="城市">
              <el-input v-model="form.city" placeholder="如：肇庆" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" placeholder="一句话卖点，如：岭南山水 · 两天一夜慢游" />
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="天数">
              <el-input-number v-model="form.days" :min="1" :max="30" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="封面图">
              <div class="cover-upload">
                <el-upload
                  class="cover-picker"
                  :auto-upload="false"
                  :show-file-list="false"
                  :file-list="coverFileList"
                  accept="image/png,image/jpeg"
                  :on-change="handleCoverChange"
                >
                  <div v-loading="coverUploading" class="cover-box">
                    <img v-if="form.cover" :src="form.cover" class="cover-img" alt="封面预览" />
                    <div v-else class="cover-empty">
                      <el-icon><Plus /></el-icon>
                      <span>上传图片</span>
                    </div>
                  </div>
                </el-upload>
                <div class="cover-tips">
                  <p>支持 png / jpg，建议 16:9（如 1200×675），单张不超过 10MB</p>
                  <p>点图片可更换，上传成功后自动带出路径</p>
                  <el-button v-if="form.cover" link type="danger" @click="form.cover = ''">移除封面</el-button>
                  <p v-if="form.cover" class="cover-url">{{ form.cover }}</p>
                </div>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="来源链接">
          <div class="source-row">
            <el-input v-model="form.sourceUrl" placeholder="外部游记链接（如小红书），可留空" />
          </div>
        </el-form-item>

        <el-form-item label="行程原文">
          <el-input
            v-model="parseContent"
            type="textarea"
            :rows="5"
            placeholder="把游记/攻略的正文或要点粘到这里（可选）。留空时会自动抓取上面「来源链接」的正文用于生成；想改内容就先抓一次再在这里编辑。"
          />
        </el-form-item>

        <el-form-item label=" ">
          <el-button type="primary" plain :loading="parsing" @click="parseLink">解析生成行程</el-button>
          <span class="hint-text">只填链接也能解析（会自动抓正文）；生成需要 2~7 分钟，请耐心等待；结果会填到下面的行程内容里，可再手工改</span>
        </el-form-item>

        <el-form-item label="行程内容">
          <el-input
            v-model="form.tripJson"
            type="textarea"
            :rows="14"
            placeholder='完整行程 JSON，结构：{"title":"...","toCity":"...","days":2,"dayPlans":[{"day":1,"schedules":[{"time":"09:00-11:30","title":"七星岩","ticket":"78元","openTime":"07:30-17:30","location":"..."}]}]}'
          />
        </el-form-item>

        <el-alert type="info" :closable="false" class="tip">
          只填「来源链接」时，后端会自动抓取该网页的正文并据此生成行程（小红书这类分享页实测能取到，
          抓到的正文会回填到上面的「行程原文」）。遇到登录墙或纯异步渲染的站点抓不到，就把正文粘进「行程原文」。
          「城市」留空也可以，AI 会从正文里判断目的地。
        </el-alert>

        <el-form-item label=" ">
          <el-button @click="validateJson">格式校验</el-button>
          <span v-if="jsonHint" :class="jsonOk ? 'ok-text' : 'err-text'">{{ jsonHint }}</span>
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
import { Plus } from '@element-plus/icons-vue'
import type { UploadUserFile } from 'element-plus'
import {
  getFeaturedList,
  addFeatured,
  updateFeatured,
  deleteFeatured,
  updateFeaturedStatus,
  parseFeaturedLink,
  type FeaturedTrip,
  type FeaturedTripSaveParams
} from '@/api/featured'
import { uploadImage } from '@/api/upload'

const loading = ref(false)
const parsing = ref(false)
const list = ref<FeaturedTrip[]>([])
const jsonHint = ref('')
const jsonOk = ref(false)
/** 行程原文：仅用于「解析生成」，不随表单保存 */
const parseContent = ref('')
/** 封面图上传中 */
const coverUploading = ref(false)
/** el-upload 内部列表：关掉文件列表展示后，靠它允许重复选同一张图 */
const coverFileList = ref<UploadUserFile[]>([])

const form = reactive<FeaturedTripSaveParams & { visible: boolean; saving: boolean }>({
  visible: false,
  saving: false,
  title: '',
  tripJson: '',
  sortOrder: 0,
  status: 1,
  days: 1
})

async function load() {
  loading.value = true
  try {
    list.value = await getFeaturedList()
  } finally {
    loading.value = false
  }
}

function openForm(row?: FeaturedTrip) {
  jsonHint.value = ''
  parseContent.value = ''
  if (row) {
    Object.assign(form, {
      id: row.id,
      title: row.title || '',
      subtitle: row.subtitle || '',
      city: row.city || '',
      days: row.days ?? 1,
      cover: row.cover || '',
      tripJson: row.tripJson || '',
      sourceUrl: row.sourceUrl || '',
      sortOrder: row.sortOrder ?? 0,
      status: row.status ?? 1
    })
  } else {
    Object.assign(form, {
      id: undefined,
      title: '',
      subtitle: '',
      city: '',
      days: 1,
      cover: '',
      tripJson: '',
      sourceUrl: '',
      sortOrder: 0,
      status: 1
    })
  }
  form.visible = true
}

/**
 * 选好封面图后自己上传（el-upload 关掉自动上传，这样能带着管理端 token 走统一请求封装），
 * 上传成功把返回的 URL 写进 form.cover，界面直接渲染预览。
 */
async function handleCoverChange(file: UploadUserFile) {
  const raw = file.raw
  coverFileList.value = [] // 清掉内部列表，允许重复选同一张图
  if (!raw) return
  if (!/^image\/(png|jpeg)$/.test(raw.type)) {
    ElMessage.warning('封面只支持 png / jpg 图片')
    return
  }
  if (raw.size > 10 * 1024 * 1024) {
    ElMessage.warning('封面图片不要超过 10MB')
    return
  }
  coverUploading.value = true
  try {
    form.cover = await uploadImage(raw)
    ElMessage.success('封面已上传')
  } catch (e) {
    console.warn('封面上传失败:', e)
  } finally {
    coverUploading.value = false
  }
}

/**
 * 解析生成行程草稿并回填表单（不落库）。
 * 城市为空也行：只给链接时后端会自动抓取网页正文，用正文来生成。
 */
async function parseLink() {
  const city = (form.city || '').trim()
  const content = parseContent.value.trim()
  const sourceUrl = (form.sourceUrl || '').trim()
  if (!city && !content && !sourceUrl) {
    ElMessage.warning('请至少填「城市」「来源链接」或「行程原文」中的一项')
    return
  }
  parsing.value = true
  try {
    const draft = await parseFeaturedLink({ sourceUrl, city, content })
    if (draft) {
      // 后端会带着抓到的正文回来，填进「行程原文」让人看到这次到底拿什么生成的
      if (draft.sourceText) {
        parseContent.value = draft.sourceText
        ElMessage.info(`已自动抓取链接正文 ${draft.sourceText.length} 字，正在据此生成行程`)
      }
      form.title = draft.title || form.title
      form.city = draft.city || form.city
      form.days = draft.days ?? form.days
      form.tripJson = draft.tripJson || form.tripJson
      jsonHint.value = ''
      const dayCount = draft.days ?? 0
      if (!dayCount) {
        ElMessage.warning('没生成出行程，请检查城市 / 链接内容后重试')
      } else {
        ElMessage.success(`已生成 ${dayCount} 天行程草稿，请核对内容后再保存`)
      }
    }
  } catch (e) {
    console.warn('解析生成失败:', e)
  } finally {
    parsing.value = false
  }
}

function validateJson() {
  if (!form.tripJson) {
    jsonOk.value = false
    jsonHint.value = '行程内容为空'
    return
  }
  try {
    const obj = JSON.parse(form.tripJson)
    const days = Array.isArray(obj?.dayPlans) ? obj.dayPlans.length : 0
    const spots = Array.isArray(obj?.dayPlans)
      ? obj.dayPlans.reduce((n: number, d: any) => n + (Array.isArray(d?.schedules) ? d.schedules.length : 0), 0)
      : 0
    jsonOk.value = true
    jsonHint.value = `格式正确：${days} 天、${spots} 个地点`
  } catch (e) {
    jsonOk.value = false
    jsonHint.value = '不是合法 JSON，请检查'
  }
}

async function save() {
  if (!form.title) {
    ElMessage.warning('请填标题')
    return
  }
  if (!form.tripJson) {
    ElMessage.warning('请填行程内容，或先点「解析链接生成行程」')
    return
  }
  form.saving = true
  try {
    if (form.id) {
      await updateFeatured({ ...form })
      ElMessage.success('已保存')
    } else {
      await addFeatured({ ...form })
      ElMessage.success('已新增')
    }
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}

function toggleStatus(row: FeaturedTrip, status: number) {
  const action = status === 1 ? '上架' : '下架'
  ElMessageBox.confirm(`确定${action}「${row.title || row.id}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await updateFeaturedStatus(row.id, status)
      ElMessage.success(`已${action}`)
      load()
    })
    .catch(() => {})
}

function remove(row: FeaturedTrip) {
  ElMessageBox.confirm(`确定删除精选行程「${row.title || row.id}」？`, '删除确认', { type: 'error' })
    .then(async () => {
      await deleteFeatured(row.id)
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

.source-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.tip {
  margin-bottom: 12px;
}

.hint-text {
  margin-left: 12px;
  color: #94a3b8;
  font-size: 13px;
}

.ok-text {
  margin-left: 12px;
  color: #22c55e;
  font-size: 13px;
}

.err-text {
  margin-left: 12px;
  color: #ef4444;
  font-size: 13px;
}

/* 封面上传：预览框 + 右侧说明 */
.cover-upload {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.cover-box {
  width: 168px;
  height: 94px;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s;
}

.cover-box:hover {
  border-color: var(--el-color-primary);
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: #94a3b8;
  font-size: 12px;
}

.cover-tips {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.7;
}

.cover-tips p {
  margin: 0;
}

.cover-url {
  max-width: 260px;
  word-break: break-all;
  color: #cbd5e1;
}
</style>
