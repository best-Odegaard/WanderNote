<template>
  <div class="page-card">
    <div class="table-toolbar">
      <span class="text-muted">共 {{ total }} 条</span>
      <el-button type="primary" @click="openForm()">新增管理员</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="130" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column prop="roleName" label="角色" width="130">
        <template #default="{ row }">
          <el-tag v-if="row.roleName" size="small" effect="plain">{{ row.roleName }}</el-tag>
          <span v-else class="text-muted">未分配</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="最后登录" width="160">
        <template #default="{ row }">{{ row.lastLoginTime || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" />
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
    <el-dialog v-model="form.visible" :title="form.id ? '编辑管理员' : '新增管理员'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="登录账号" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item :label="form.id ? '新密码' : '密码'" :prop="form.id ? '' : 'password'">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="form.id ? '留空则不修改' : '初始密码'"
          />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="昵称（可空）" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleId" placeholder="选择角色" clearable style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
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
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getAdminList,
  addAdmin,
  updateAdmin,
  deleteAdmin,
  updateAdminStatus,
  getRoleList,
  type AdminSaveParams
} from '@/api/manage'
import type { AdminItem, RoleItem } from '@/types'

const loading = ref(false)
const list = ref<AdminItem[]>([])
const roles = ref<RoleItem[]>([])
const total = ref(0)
const query = reactive<{ pageNum: number; pageSize: number }>({ pageNum: 1, pageSize: 10 })

const formRef = ref<FormInstance>()
const form = reactive<AdminSaveParams & { visible: boolean; saving: boolean }>({
  visible: false,
  saving: false,
  username: '',
  password: '',
  nickname: '',
  status: 1
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res = await getAdminList(query)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  roles.value = await getRoleList()
}

function onPageChange(page: number) {
  query.pageNum = page
  load()
}

function openForm(row?: AdminItem) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      username: row.username,
      password: '',
      nickname: row.nickname || '',
      roleId: row.roleId || undefined,
      status: row.status ?? 1
    })
  } else {
    Object.assign(form, {
      id: undefined,
      username: '',
      password: '',
      nickname: '',
      roleId: undefined,
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
      await updateAdmin({ ...form })
      ElMessage.success('已保存')
    } else {
      await addAdmin({ ...form })
      ElMessage.success('已新增')
    }
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}

function toggleStatus(row: AdminItem, status: number) {
  const action = status === 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确定${action}管理员「${row.username}」？`, '提示', { type: 'warning' })
    .then(async () => {
      await updateAdminStatus(row.id, status)
      ElMessage.success(`已${action}`)
      load()
    })
    .catch(() => {})
}

function remove(row: AdminItem) {
  ElMessageBox.confirm(`确定删除管理员「${row.username}」？删除后不可恢复。`, '删除确认', { type: 'error' })
    .then(async () => {
      await deleteAdmin(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(() => {
  load()
  loadRoles()
})
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
