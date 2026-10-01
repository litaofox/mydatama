<template>
  <div class="page-card">
    <div class="filter-bar">
      <h3 class="card-title">ABAC 策略</h3>
      <el-button type="primary" @click="createVisible = true">新增策略</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="code" label="编码" width="160" />
      <el-table-column prop="name" label="名称" width="180" />
      <el-table-column prop="resource" label="资源" width="130" />
      <el-table-column prop="action" label="动作" width="100" />
      <el-table-column label="效果" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.effect === 'ALLOW' ? 'success' : 'danger'">{{ row.effect }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="conditionTree" label="条件树" min-width="220" show-overflow-tooltip />
      <el-table-column prop="priority" label="优先级" width="80" />
      <el-table-column label="启用" width="100" fixed="right">
        <template #default="{ row }">
          <el-switch :model-value="!!row.enabled" @change="(v: boolean) => onToggle(row, v)" />
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新增 ABAC 策略" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="编码" required><el-input v-model="form.code" placeholder="如 POLICY-SECRET-LIMIT" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="资源" required><el-input v-model="form.resource" placeholder="如 asset / file" /></el-form-item>
        <el-form-item label="动作" required><el-input v-model="form.action" placeholder="如 read / write" /></el-form-item>
        <el-form-item label="效果">
          <el-select v-model="form.effect" style="width: 100%">
            <el-option value="ALLOW" label="ALLOW（允许）" />
            <el-option value="DENY" label="DENY（拒绝）" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件树" required>
          <el-input v-model="form.conditionTree" type="textarea" :rows="4"
            placeholder='JSON，如 {"all":[{"eq":["user.secretLevel","2"]}]}' />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="999" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getPolicies, createPolicy, togglePolicy, type IamPolicy } from '@/api/iam'

const list = ref<IamPolicy[]>([])
const loading = ref(false)
const saving = ref(false)
const createVisible = ref(false)

const form = reactive({
  code: '',
  name: '',
  resource: '',
  action: '',
  effect: 'ALLOW',
  conditionTree: '',
  priority: 100
})

async function load() {
  loading.value = true
  try {
    list.value = await getPolicies()
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  if (!form.code || !form.name || !form.resource || !form.action) {
    ElMessage.warning('编码/名称/资源/动作必填')
    return
  }
  try {
    JSON.parse(form.conditionTree)
  } catch {
    ElMessage.warning('条件树必须是合法 JSON')
    return
  }
  saving.value = true
  try {
    await createPolicy({ ...form })
    ElMessage.success('创建成功')
    createVisible.value = false
    Object.assign(form, { code: '', name: '', resource: '', action: '', effect: 'ALLOW', conditionTree: '', priority: 100 })
    load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function onToggle(row: IamPolicy, enabled: boolean) {
  try {
    await togglePolicy(row.id, enabled)
    row.enabled = enabled
    ElMessage.success(enabled ? '已启用' : '已停用')
  } catch {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<style scoped>
.card-title {
  margin: 0;
  font-size: 15px;
  flex: 1;
}
</style>
