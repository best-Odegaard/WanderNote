<template>
  <div class="page-card">
    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="用户名 / 昵称 / 手机号"
        clearable
        style="width: 220px"
        @keyup.enter="load"
        @clear="load"
      />
      <el-select v-model="query.status" placeholder="账号状态" clearable style="width: 130px">
        <el-option label="正常" :value="1" />
        <el-option label="已禁用" :value="0" />
      </el-select>
      <!-- 选项来自 /admin/user-profile/tags，与后端受控词表同一份 -->
      <el-select
        v-model="query.profileTag"
        placeholder="画像标签"
        clearable
        filterable
        style="width: 150px"
      >
        <el-option v-for="tag in tagOptions" :key="tag" :label="tag" :value="tag" />
      </el-select>
      <el-input
        v-model="query.profileMark"
        placeholder="画像标记"
        clearable
        style="width: 140px"
        @keyup.enter="load"
        @clear="load"
      />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button :loading="exporting" @click="onExport">导出</el-button>
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
      <el-table-column label="登录账号" width="120">
        <template #default="{ row }">{{ row.username }}</template>
      </el-table-column>
      <el-table-column label="登录密码" width="140">
        <template #default="{ row }">
          <el-text v-if="row.password" class="password-text">{{ row.password }}</el-text>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="130">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column prop="points" label="积分" width="70" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- AI 画像：前 3 个标签（带权重）+ 摘要两行截断 -->
      <el-table-column label="AI 画像" min-width="220">
        <template #default="{ row }">
          <template v-if="row.profileTags && row.profileTags.length">
            <div class="profile-tags">
              <el-tag
                v-for="tag in row.profileTags"
                :key="tag.tag"
                size="small"
                effect="plain"
                type="success"
              >
                {{ tag.tag }}<span class="tag-weight">×{{ tag.weight }}</span>
              </el-tag>
            </div>
            <div class="profile-summary text-muted">{{ row.profileSummary || '暂无摘要' }}</div>
          </template>
          <span v-else class="text-muted">暂无画像</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="160" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openProfile(row)">画像</el-button>
          <el-button v-if="row.status === 1" link type="danger" @click="toggleStatus(row, 0)">禁用</el-button>
          <el-button v-else link type="success" @click="toggleStatus(row, 1)">启用</el-button>
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

    <!-- ==================== 画像抽屉 ==================== -->
    <el-drawer v-model="drawer.visible" title="AI 画像" size="620px" @closed="onDrawerClosed">
      <div v-loading="drawer.loading" class="drawer-body">
        <template v-if="drawer.detail">
          <!-- 基础信息 -->
          <div class="section-title">基础信息</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="用户">
              {{ drawer.detail.nickname || '-' }}（@{{ drawer.detail.username }}）
            </el-descriptions-item>
            <el-descriptions-item label="手机号">{{ drawer.detail.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="对话轮数">{{ drawer.detail.chatRounds }}</el-descriptions-item>
            <el-descriptions-item label="偏好记忆开关">
              <el-tag :type="drawer.detail.injectEnabled === 1 ? 'success' : 'info'" size="small">
                {{ drawer.detail.injectEnabled === 1 ? '开启' : '已由用户关闭' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="画像更新时间">
              {{ drawer.detail.updateTime || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="更新来源">
              <el-tag size="small" :type="sourceTagType(drawer.detail.lastUpdateSource)">
                {{ sourceLabel(drawer.detail.lastUpdateSource) }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="!drawer.detail.hasProfile"
            class="mt-12"
            type="info"
            :closable="false"
            show-icon
            title="该用户还没有沉淀出画像（未在 AI 对话中生成过行程）"
          />

          <!-- 受控偏好标签 -->
          <div class="section-title">受控偏好标签（权重 = 命中次数）</div>
          <template v-if="!drawer.editing">
            <div v-if="drawer.detail.preferenceTags.length" class="profile-tags">
              <el-tag
                v-for="tag in drawer.detail.preferenceTags"
                :key="tag.tag"
                size="small"
                effect="plain"
                type="success"
              >
                {{ tag.tag }}<span class="tag-weight">×{{ tag.weight }}</span>
              </el-tag>
            </div>
            <span v-else class="text-muted">无</span>
          </template>
          <template v-else>
            <div v-for="(row, idx) in form.preferenceTags" :key="idx" class="tag-edit-row">
              <el-select v-model="row.tag" placeholder="选择标签" filterable style="width: 180px">
                <el-option v-for="tag in tagOptions" :key="tag" :label="tag" :value="tag" />
              </el-select>
              <el-input-number v-model="row.weight" :min="1" :max="9999" size="default" />
              <el-button link type="danger" @click="form.preferenceTags.splice(idx, 1)">删除</el-button>
            </div>
            <el-button link type="primary" @click="addTagRow">+ 添加标签</el-button>
          </template>

          <!-- 结构化偏好 -->
          <div class="section-title">结构化偏好</div>
          <template v-if="!drawer.editing">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="预算档位">
                {{ drawer.detail.budgetLevel || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="出行节奏">{{ drawer.detail.pace || '-' }}</el-descriptions-item>
              <el-descriptions-item label="同行人">
                {{ drawer.detail.companions || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="偏好天数">
                {{ drawer.detail.preferDays ? `${drawer.detail.preferDays} 天` : '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </template>
          <template v-else>
            <el-form label-width="90px" class="edit-form">
              <el-form-item label="预算档位">
                <el-select v-model="form.budgetLevel" clearable placeholder="未判断出" style="width: 180px">
                  <el-option v-for="v in BUDGET_LEVELS" :key="v" :label="v" :value="v" />
                </el-select>
              </el-form-item>
              <el-form-item label="出行节奏">
                <el-select v-model="form.pace" clearable placeholder="未判断出" style="width: 180px">
                  <el-option v-for="v in PACE_LEVELS" :key="v" :label="v" :value="v" />
                </el-select>
              </el-form-item>
              <el-form-item label="同行人">
                <el-select v-model="form.companions" clearable placeholder="未判断出" style="width: 180px">
                  <el-option v-for="v in COMPANION_LEVELS" :key="v" :label="v" :value="v" />
                </el-select>
              </el-form-item>
              <el-form-item label="偏好天数">
                <el-input-number v-model="form.preferDays" :min="1" :max="60" placeholder="清空表示未知" />
              </el-form-item>
            </el-form>
          </template>

          <!-- 自由标签 -->
          <div class="section-title">自由标签（仅展示，不参与筛选）</div>
          <template v-if="!drawer.editing">
            <div v-if="drawer.detail.freeTags.length" class="profile-tags">
              <el-tag v-for="t in drawer.detail.freeTags" :key="t" size="small" effect="plain">
                {{ t }}
              </el-tag>
            </div>
            <span v-else class="text-muted">无</span>
          </template>
          <el-input
            v-else
            v-model="form.freeTagsText"
            type="textarea"
            :rows="3"
            placeholder="一行一个标签"
          />

          <!-- 硬性约束 -->
          <div class="section-title">硬性约束</div>
          <template v-if="!drawer.editing">
            <div>{{ drawer.detail.constraintsText || '无' }}</div>
          </template>
          <el-input
            v-else
            v-model="form.constraintsText"
            type="textarea"
            :rows="2"
            placeholder="多个用中文分号；分隔，例如：不吃辣；不爬山"
          />

          <!-- AI 画像摘要 -->
          <div class="section-title">AI 画像摘要</div>
          <template v-if="!drawer.editing">
            <div class="summary-block">{{ drawer.detail.summaryText || '暂无摘要' }}</div>
            <div v-if="drawer.detail.summaryTime" class="text-muted summary-time">
              生成于 {{ drawer.detail.summaryTime }}
            </div>
          </template>
          <el-input v-else v-model="form.summaryText" type="textarea" :rows="4" />

          <!-- 运营备注：独立保存，不和画像编辑一起提交 -->
          <div class="section-title">运营备注 / 标记</div>
          <el-input
            v-model="remarkForm.remark"
            type="textarea"
            :rows="2"
            placeholder="仅运营可见，用户端不展示"
          />
          <div class="remark-row">
            <el-input v-model="remarkForm.profileMark" placeholder="标记，例如：高价值用户" style="width: 220px" />
            <el-button :loading="drawer.remarkSaving" @click="saveRemark">保存备注</el-button>
          </div>

          <!-- 底部操作 -->
          <div class="drawer-footer">
            <el-button v-if="!drawer.editing" @click="startEdit">编辑画像</el-button>
            <template v-else>
              <el-button type="primary" :loading="drawer.saving" @click="saveProfile">保存画像</el-button>
              <el-button @click="drawer.editing = false">取消</el-button>
            </template>
          </div>
          <div class="text-muted drawer-tip">
            人工修改即时生效，用户下次生成行程时会被 AI 重写覆盖（当前不做锁定）。
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, updateUserStatus, type UserQuery } from '@/api/user'
import {
  exportUserList,
  getProfileTags,
  getUserProfile,
  updateUserProfile,
  updateUserProfileRemark
} from '@/api/userProfile'
import { downloadCsv } from '@/utils/request'
import type { ProfileTag, UserItem, UserProfileDetail } from '@/types'

/**
 * 三个固定字段的取值全集。
 * 权威定义在后端 com.gkv.constant.ProfileConstant（后端会对非法值做校验并视为清空），
 * 这里与之保持一致；标签词表则走接口拿，不在这里硬编码。
 */
const BUDGET_LEVELS = ['经济', '适中', '高档']
const PACE_LEVELS = ['悠闲', '常规', '暴走']
const COMPANION_LEVELS = ['独自', '情侣', '朋友', '家庭']

const loading = ref(false)
const exporting = ref(false)
const list = ref<UserItem[]>([])
const total = ref(0)
const tagOptions = ref<string[]>([])
const query = reactive<{
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number
  profileTag?: string
  profileMark?: string
}>({
  pageNum: 1,
  pageSize: 10
})

/** 画像抽屉 */
const drawer = reactive({
  visible: false,
  loading: false,
  editing: false,
  saving: false,
  remarkSaving: false,
  userId: 0,
  detail: null as UserProfileDetail | null
})

/** 画像内容编辑态（整份覆盖） */
const form = reactive({
  preferenceTags: [] as ProfileTag[],
  freeTagsText: '',
  constraintsText: '',
  budgetLevel: '',
  pace: '',
  companions: '',
  preferDays: null as number | null,
  summaryText: ''
})

/** 运营备注独立表单（独立保存按钮） */
const remarkForm = reactive({ remark: '', profileMark: '' })

async function load() {
  loading.value = true
  try {
    const res = await getUserList(query as UserQuery)
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

async function loadTagOptions() {
  try {
    tagOptions.value = await getProfileTags()
  } catch {
    // 词表拉不到不阻塞页面：标签筛选与标签编辑会退化成空下拉
    tagOptions.value = []
  }
}

async function openProfile(row: UserItem) {
  drawer.visible = true
  drawer.editing = false
  drawer.userId = row.id
  drawer.detail = null
  await refreshDetail()
}

async function refreshDetail() {
  drawer.loading = true
  try {
    const detail = await getUserProfile(drawer.userId)
    drawer.detail = detail
    fillForms(detail)
  } finally {
    drawer.loading = false
  }
}

/** 用服务端返回的值填编辑态表单（保存后也重新走这里，避免用本地拼的值渲染） */
function fillForms(detail: UserProfileDetail) {
  form.preferenceTags = (detail.preferenceTags || []).map((t) => ({ tag: t.tag, weight: t.weight }))
  form.freeTagsText = (detail.freeTags || []).join('\n')
  form.constraintsText = detail.constraintsText || ''
  form.budgetLevel = detail.budgetLevel || ''
  form.pace = detail.pace || ''
  form.companions = detail.companions || ''
  form.preferDays = detail.preferDays ?? null
  form.summaryText = detail.summaryText || ''
  remarkForm.remark = detail.remark || ''
  remarkForm.profileMark = detail.profileMark || ''
}

function startEdit() {
  if (drawer.detail) fillForms(drawer.detail)
  drawer.editing = true
}

function addTagRow() {
  form.preferenceTags.push({ tag: '', weight: 1 })
}

async function saveProfile() {
  const tags = form.preferenceTags.filter((t) => !!t.tag)
  const freeTags = form.freeTagsText
    .split('\n')
    .map((s) => s.trim())
    .filter((s) => !!s)
  drawer.saving = true
  try {
    await updateUserProfile(drawer.userId, {
      preferenceTags: tags,
      freeTags,
      constraintsText: form.constraintsText,
      budgetLevel: form.budgetLevel,
      pace: form.pace,
      companions: form.companions,
      preferDays: form.preferDays,
      summaryText: form.summaryText
    })
    ElMessage.success('画像已保存')
    drawer.editing = false
    // 以服务端返回为准重新拉取，不用前端本地拼的值渲染
    await refreshDetail()
    load()
  } finally {
    drawer.saving = false
  }
}

async function saveRemark() {
  drawer.remarkSaving = true
  try {
    await updateUserProfileRemark(drawer.userId, {
      remark: remarkForm.remark,
      profileMark: remarkForm.profileMark
    })
    ElMessage.success('备注已保存')
    await refreshDetail()
    load()
  } finally {
    drawer.remarkSaving = false
  }
}

function onDrawerClosed() {
  drawer.detail = null
  drawer.editing = false
}

function sourceLabel(source?: string) {
  if (source === 'model') return 'AI摘要'
  if (source === 'manual') return '人工修正'
  if (source === 'rule') return '规则沉淀'
  return '-'
}

function sourceTagType(source?: string) {
  if (source === 'manual') return 'warning'
  if (source === 'model') return 'success'
  return 'info'
}

function dateStamp() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`
}

async function onExport() {
  exporting.value = true
  try {
    // 导出不带分页参数：后端按筛选条件出全量（有上限）
    const { pageNum, pageSize, ...filters } = query
    const rows = await exportUserList(filters as unknown as Record<string, unknown>)
    if (!rows.length) {
      ElMessage.warning('当前筛选条件下没有可导出的数据')
      return
    }
    const header = [
      'ID', '用户名', '昵称', '手机号', '状态', '注册时间',
      '画像标签', '画像摘要', '对话轮数', '画像更新时间', '更新来源', '运营备注', '运营标记'
    ]
    const body = rows.map((r) => [
      r.id,
      r.username,
      r.nickname,
      r.phone,
      r.status === 1 ? '正常' : '已禁用',
      r.createTime,
      (r.profileTags || []).map((t) => `${t.tag}(${t.weight})`).join('、'),
      r.profileSummary,
      r.profileChatRounds ?? 0,
      r.profileUpdateTime,
      sourceLabel(r.profileSource),
      r.profileRemark,
      r.profileMark
    ])
    downloadCsv(`用户画像_${dateStamp()}.csv`, [header, ...body])
    ElMessage.success(`已导出 ${rows.length} 条`)
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  load()
  loadTagOptions()
})
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

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 4px;
}

.tag-weight {
  margin-left: 4px;
  opacity: 0.7;
}

/* 摘要两行截断 */
.profile-summary {
  font-size: 12px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.drawer-body {
  min-height: 200px;
}

.section-title {
  margin: 18px 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.section-title:first-child {
  margin-top: 0;
}

.mt-12 {
  margin-top: 12px;
}

.tag-edit-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.edit-form {
  margin-top: 4px;
}

.summary-block {
  white-space: pre-wrap;
  line-height: 1.6;
}

.summary-time {
  margin-top: 4px;
  font-size: 12px;
}

.remark-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.drawer-footer {
  margin-top: 24px;
  display: flex;
  gap: 10px;
}

.drawer-tip {
  margin-top: 10px;
  font-size: 12px;
}
</style>
