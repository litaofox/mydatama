<template>
  <div class="page-card">
    <div class="filter-bar">
      <el-input v-model="targetRef" placeholder="目标引用，如 asset:1" style="width: 240px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <span class="text-muted">按 targetRef 精确查询资产挂载的质量规则</span>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="targetRef" label="目标引用" width="160" />
      <el-table-column prop="checkType" label="检查类型" width="140" />
      <el-table-column prop="expr" label="表达式" min-width="240" show-overflow-tooltip />
      <el-table-column prop="standardId" label="关联标准" width="100">
        <template #default="{ row }">{{ row.standardId || '-' }}</template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !list.length" description="请输入 targetRef 查询，如 asset:1" :image-size="80" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { getQualityRules, type QualityRule } from '@/api/governance'

const targetRef = ref('')
const list = ref<QualityRule[]>([])
const loading = ref(false)

async function load() {
  if (!targetRef.value.trim()) return
  loading.value = true
  try {
    list.value = await getQualityRules(targetRef.value.trim())
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>
