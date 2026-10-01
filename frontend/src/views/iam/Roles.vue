<template>
  <div class="page-card">
    <h3 class="card-title">角色管理</h3>
    <el-table :data="roles" v-loading="loading">
      <el-table-column prop="code" label="编码" width="140" />
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.builtin ? 'warning' : 'info'">{{ row.builtin ? '内置' : '自定义' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openPerm(row)">配置权限</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="permVisible" :title="`配置权限 — ${currentRole?.name}`" width="640px">
      <div v-for="(group, module) in groupedPermissions" :key="module" class="perm-group">
        <div class="perm-module">
          <el-checkbox
            :model-value="isModuleAllChecked(group)"
            :indeterminate="isModuleIndeterminate(group)"
            @change="(v: boolean) => toggleModule(group, v)"
          ><b>{{ module }}</b></el-checkbox>
        </div>
        <div class="perm-items">
          <el-checkbox
            v-for="perm in group"
            :key="perm.id"
            v-model="checkedIds[perm.id]"
            :label="perm.id"
            class="perm-item"
          >{{ perm.name }}（{{ perm.code }}）</el-checkbox>
        </div>
      </div>
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSavePerms">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getRoles, getPermissions, getRolePermissions, updateRolePermissions, type IamRole, type IamPermission } from '@/api/iam'

const roles = ref<IamRole[]>([])
const permissions = ref<IamPermission[]>([])
const loading = ref(false)
const saving = ref(false)
const permVisible = ref(false)
const currentRole = ref<IamRole | null>(null)
const checkedIds = reactive<Record<number, boolean>>({})

const groupedPermissions = computed(() => {
  const groups: Record<string, IamPermission[]> = {}
  for (const p of permissions.value) {
    const m = p.module || '其他'
    if (!groups[m]) groups[m] = []
    groups[m].push(p)
  }
  return groups
})

function checkedList(): number[] {
  return Object.entries(checkedIds).filter(([, v]) => v).map(([k]) => Number(k))
}

function isModuleAllChecked(group: IamPermission[]): boolean {
  return group.every((p) => checkedIds[p.id])
}

function isModuleIndeterminate(group: IamPermission[]): boolean {
  const checked = group.filter((p) => checkedIds[p.id]).length
  return checked > 0 && checked < group.length
}

function toggleModule(group: IamPermission[], checked: boolean) {
  for (const p of group) checkedIds[p.id] = checked
}

async function load() {
  loading.value = true
  try {
    const [r, perms] = await Promise.all([getRoles(), getPermissions()])
    roles.value = r
    permissions.value = perms
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function openPerm(role: IamRole) {
  currentRole.value = role
  Object.keys(checkedIds).forEach((k) => delete checkedIds[Number(k)])
  try {
    const ids = await getRolePermissions(role.id)
    for (const p of permissions.value) {
      checkedIds[p.id] = ids.includes(p.id)
    }
    permVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

async function onSavePerms() {
  if (!currentRole.value) return
  saving.value = true
  try {
    await updateRolePermissions(currentRole.value.id, checkedList())
    ElMessage.success('权限保存成功')
    permVisible.value = false
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-title {
  margin: 0 0 16px;
  font-size: 15px;
}
.perm-group {
  margin-bottom: 12px;
  border-bottom: 1px dashed #ebeef5;
  padding-bottom: 8px;
}
.perm-module {
  margin-bottom: 6px;
}
.perm-items {
  padding-left: 24px;
  display: flex;
  flex-wrap: wrap;
}
.perm-item {
  width: 48%;
  margin-right: 0;
}
</style>
