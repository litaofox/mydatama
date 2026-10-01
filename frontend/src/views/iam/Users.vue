<template>
  <div class="page-card">
    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="用户名/姓名" clearable style="width: 200px" @keyup.enter="load(1)" />
      <el-button type="primary" @click="load(1)">查询</el-button>
      <div class="spacer"></div>
      <el-button type="primary" @click="createVisible = true">新增用户</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="username" label="用户名" width="130" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="deptCode" label="部门" width="110" />
      <el-table-column label="密级" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="secretLevelTagType(row.secretLevel)">{{ secretLevelText(row.secretLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近登录" width="165">
        <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" layout="total, prev, pager, next" :total="total" :page-size="query.size"
      :current-page="query.page" @current-change="load" />

    <el-dialog v-model="createVisible" title="新增用户" width="480px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="用户名" required><el-input v-model="createForm.username" /></el-form-item>
        <el-form-item label="密码" required><el-input v-model="createForm.password" type="password" show-password /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="createForm.realName" /></el-form-item>
        <el-form-item label="部门"><el-input v-model="createForm.deptCode" /></el-form-item>
        <el-form-item label="密级">
          <el-select v-model="createForm.secretLevel" style="width: 100%">
            <el-option :value="1" label="1 公开" />
            <el-option :value="2" label="2 内部" />
            <el-option :value="3" label="3 秘密" />
            <el-option :value="4" label="4 机密" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" :title="`编辑用户 — ${editRow?.username}`" width="480px">
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="姓名"><el-input v-model="editForm.realName" /></el-form-item>
        <el-form-item label="部门"><el-input v-model="editForm.deptCode" /></el-form-item>
        <el-form-item label="密级">
          <el-select v-model="editForm.secretLevel" style="width: 100%">
            <el-option :value="1" label="1 公开" />
            <el-option :value="2" label="2 内部" />
            <el-option :value="3" label="3 秘密" />
            <el-option :value="4" label="4 机密" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="editForm.enabled" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.roleIds" multiple style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onUpdate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getUsers, createUser, updateUser, getRoles, type IamUser, type IamRole } from '@/api/iam'
import { secretLevelText, secretLevelTagType, formatTime } from '@/utils/format'

const list = ref<IamUser[]>([])
const roles = ref<IamRole[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const query = reactive({ page: 1, size: 10, keyword: '' })

const createVisible = ref(false)
const createForm = reactive({ username: '', password: '', realName: '', deptCode: '', secretLevel: 2 })

const editVisible = ref(false)
const editRow = ref<IamUser | null>(null)
const editForm = reactive({ realName: '', deptCode: '', secretLevel: 2, enabled: true, roleIds: [] as number[] })

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await getUsers({ page: query.page, size: query.size, keyword: query.keyword || undefined })
    list.value = data.list
    total.value = data.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  if (!createForm.username || !createForm.password || !createForm.realName) {
    ElMessage.warning('用户名/密码/姓名必填')
    return
  }
  saving.value = true
  try {
    await createUser({ ...createForm })
    ElMessage.success('创建成功')
    createVisible.value = false
    Object.assign(createForm, { username: '', password: '', realName: '', deptCode: '', secretLevel: 2 })
    load(1)
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function openEdit(row: IamUser) {
  editRow.value = row
  Object.assign(editForm, {
    realName: row.realName,
    deptCode: row.deptCode,
    secretLevel: row.secretLevel,
    enabled: !!row.enabled,
    roleIds: (row as any).roleIds || []
  })
  editVisible.value = true
}

async function onUpdate() {
  if (!editRow.value) return
  saving.value = true
  try {
    await updateUser(editRow.value.id, { ...editForm })
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  load(1)
  roles.value = await getRoles().catch(() => [])
})
</script>

<style scoped>
.spacer {
  flex: 1;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
