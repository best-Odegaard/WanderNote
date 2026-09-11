<template>
  <div class="page-card">
    <div class="table-toolbar">
      <span class="text-muted">共 {{ list.length }} 条</span>
      <el-button type="primary" @click="openForm()">新增角色</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="roleKey" label="角色标识" width="150" />
      <el-table-column prop="roleName" label="角色名称" width="150" />
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
      <el-table-column label="权限" min-width="300">
        <template #default="{ row }">
          <el-tag v-for="key in splitPerms(row.perms)" :key="key" size="small" effect="plain" class="perm-tag">
            {{ permName(key) }}
          </el-tag>
          <span v-if="!row.perms" class="text-muted">无权限</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="form.visible" :title="form.id ? '编辑角色' : '新增角色'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色标识" prop="roleKey">
          <el-input v-model="form.roleKey" placeholder="如：operator" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="如：运营专员" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="角色描述（可空）" />
        </el-form-item>
        <el-form-item label="权限点">
          <el-checkbox-group v-model="form.permKeys">
            <el-checkbox v-for="p in perms" :key="p.key" :value="p.key" class="perm-checkbox">
              {{ p.name }}
            </el-checkbox>
          </el-checkbox-group>
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
  getRoleList,
  addRole,
  updateRole,
  deleteRole,
  getPermList,
  type RoleSaveParams
} from '@/api/manage'
import type { RoleItem, PermItem } from '@/types'

const loading = ref(false)
const list = ref<RoleItem[]>([])
const perms = ref<PermItem[]>([])

const formRef = ref<FormInstance>()
const form = reactive<
  RoleSaveParams & { visible: boolean; saving: boolean; permKeys: string[] }
>({
  visible: false,
  saving: false,
  roleKey: '',
  roleName: '',
  description: '',
  permKeys: []
})

const rules: FormRules = {
  roleKey: [{ required: true, message: '请输入角色标识', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

const permName = (key: string) => {
  const found = perms.value.find((p) => p.key === key)
  return found ? found.name : key
}

const splitPerms = (permsStr?: string) =>
  (permsStr || '').split(',').map((s) => s.trim()).filter(Boolean)

async function load() {
  loading.value = true
  try {
    list.value = await getRoleList()
  } finally {
    loading.value = false
  }
}

async function loadPerms() {
  perms.value = await getPermList()
}

function openForm(row?: RoleItem) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      roleKey: row.roleKey,
      roleName: row.roleName,
      description: row.description || '',
      permKeys: splitPerms(row.perms)
    })
  } else {
    Object.assign(form, {
      id: undefined,
      roleKey: '',
      roleName: '',
      description: '',
      permKeys: []
    })
  }
  form.visible = true
}

async function save() {
  await formRef.value?.validate()
  form.saving = true
  try {
    const payload: RoleSaveParams = {
      id: form.id,
      roleKey: form.roleKey,
      roleName: form.roleName,
      description: form.description,
      perms: form.permKeys.join(',')
    }
    if (form.id) {
      await updateRole(payload)
      ElMessage.success('已保存')
    } else {
      await addRole(payload)
      ElMessage.success('已新增')
    }
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}

function remove(row: RoleItem) {
  ElMessageBox.confirm(`确定删除角色「${row.roleName}」？`, '删除确认', { type: 'error' })
    .then(async () => {
      await deleteRole(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(() => {
  load()
  loadPerms()
})
</script>

<style scoped>
.perm-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.perm-checkbox {
  width: 33%;
  margin-bottom: 10px;
  margin-right: 0;
}
</style>
