<template>
  <div class="page-card">
    <h3 class="card-title">开放 API 演示（计量 Key 调用）</h3>
    <p class="text-muted">模拟外部系统通过计量 Key 访问已挂牌 API_SERVICE 产品的数据行端点。</p>
    <div class="filter-bar">
      <el-input v-model="productCode" placeholder="产品编码，如 PRD-20261001-001" style="width: 240px" />
      <el-input v-model="apiKey" placeholder="X-API-Key（创建产品时下发）" style="width: 360px" show-password type="password" />
      <el-input-number v-model="size" :min="1" :max="100" style="width: 130px" />
      <el-button type="primary" :loading="loading" @click="callApi">调用</el-button>
    </div>
    <template v-if="result">
      <el-alert type="success" :closable="false" class="result-meta">
        <template #title>
          调用成功 — 共 {{ result.total ?? result.data?.total ?? '-' }} 行，当前页 {{ rows.length }} 行
        </template>
      </el-alert>
      <el-table :data="rows" size="small" border max-height="480" v-if="rows.length">
        <el-table-column v-for="col in columns" :key="col" :prop="col" :label="col" min-width="120" show-overflow-tooltip />
      </el-table>
      <pre v-else class="raw-json">{{ JSON.stringify(result, null, 2) }}</pre>
    </template>
    <pre v-if="errorText" class="raw-json error">{{ errorText }}</pre>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import axios from 'axios'

const productCode = ref('')
const apiKey = ref('')
const size = ref(10)
const loading = ref(false)
const result = ref<any>(null)
const errorText = ref('')

const rows = computed<any[]>(() => {
  const r = result.value
  if (!r) return []
  const list = r.list || r.data?.list || r.rows || []
  return Array.isArray(list) ? list : []
})

const columns = computed<string[]>(() => {
  if (!rows.value.length) return []
  const first = rows.value[0]
  return typeof first === 'object' && first ? Object.keys(first).slice(0, 12) : []
})

async function callApi() {
  if (!productCode.value || !apiKey.value) return
  loading.value = true
  result.value = null
  errorText.value = ''
  try {
    const resp = await axios.get(`/openapi/v1/products/${productCode.value}/rows`, {
      params: { page: 1, size: size.value },
      headers: { 'X-API-Key': apiKey.value }
    })
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        result.value = body.data
      } else {
        errorText.value = JSON.stringify(body, null, 2)
      }
    } else {
      result.value = body
    }
  } catch (e: any) {
    errorText.value = e.response ? JSON.stringify(e.response.data, null, 2) : e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.card-title {
  margin: 0 0 8px;
  font-size: 15px;
}
.result-meta {
  margin-bottom: 12px;
}
.raw-json {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  font-size: 12px;
  max-height: 480px;
  overflow: auto;
}
.raw-json.error {
  color: #f56c6c;
}
</style>
