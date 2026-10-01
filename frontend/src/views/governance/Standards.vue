<template>
  <div class="page-card">
    <div class="filter-bar">
      <h3 class="card-title">数据标准</h3>
      <el-button type="primary" @click="createVisible = true">新增标准</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="code" label="编码" width="160" />
      <el-table-column prop="name" label="名称" width="200" />
      <el-table-column prop="ruleExpr" label="规则表达式" min-width="220" show-overflow-tooltip />
      <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新增数据标准" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="编码" required>
          <el-input v-model="form.code" placeholder="如 STD-VEHICLE-PLATE" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="规则表达式" required>
          <el-input v-model="form.ruleExpr" type="textarea" :rows="2" placeholder="如 regex:^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-Z]..." />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
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
import { getStandards, createStandard, type Standard } from '@/api/governance'

const list = ref<Standard[]>([])
const loading = ref(false)
const saving = ref(false)
const createVisible = ref(false)
const form = reactive({ code: '', name: '', ruleExpr: '', description: '' })

async function load() {
  loading.value = true
  try {
    list.value = await getStandards()
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  if (!form.code || !form.name || !form.ruleExpr) {
    ElMessage.warning('编码/名称/规则表达式必填')
    return
  }
  saving.value = true
  try {
    await createStandard({ ...form })
    ElMessage.success('创建成功')
    createVisible.value = false
    Object.assign(form, { code: '', name: '', ruleExpr: '', description: '' })
    load()
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
  margin: 0;
  font-size: 15px;
  flex: 1;
}
</style>
