<template>
  <div class="page-card">
    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="产品名称/编码" clearable style="width: 200px" @keyup.enter="load(1)" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
        <el-option v-for="s in statuses" :key="s" :label="productStatusText(s)" :value="s" />
      </el-select>
      <el-select v-model="query.form" placeholder="形态" clearable style="width: 130px">
        <el-option label="数据包" value="DATA_PACKAGE" />
        <el-option label="API服务" value="API_SERVICE" />
        <el-option label="报告" value="REPORT" />
      </el-select>
      <el-button type="primary" @click="load(1)">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <div class="spacer"></div>
      <el-button type="primary" @click="$router.push('/product/create')">+ 创建产品</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="code" label="产品编码" width="180" />
      <el-table-column prop="name" label="产品名称" min-width="200" show-overflow-tooltip />
      <el-table-column label="形态" width="100">
        <template #default="{ row }">{{ productFormText(row.form) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="productStatusTagType(row.status)" effect="dark" size="small">
            {{ productStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="110">
        <template #default="{ row }">{{ row.category || '-' }}</template>
      </el-table-column>
      <el-table-column label="定价" width="130">
        <template #default="{ row }">
          {{ pricingText(row) }}
        </template>
      </el-table-column>
      <el-table-column label="挂牌时间" width="165">
        <template #default="{ row }">{{ formatTime(row.listedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/product/${row.id}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" layout="total, prev, pager, next" :total="total" :page-size="query.size"
      :current-page="query.page" @current-change="load" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getProducts, type Product } from '@/api/product'
import { productFormText, productStatusText, productStatusTagType, formatTime } from '@/utils/format'

const statuses = ['DRAFT', 'CONFIGURED', 'GENERATED', 'PASSED', 'BLOCKED', 'REGISTERED', 'LISTED']

const list = ref<Product[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 10, keyword: '', status: '', form: '' })

function pricingText(row: Product) {
  if (!row.pricingModel) return '-'
  const model = { PER_CALL: '按次', PER_MONTH: '按月', ONE_TIME: '买断' }[row.pricingModel] || row.pricingModel
  return row.price != null ? `${model} ¥${row.price}` : model
}

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await getProducts({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      form: query.form || undefined
    })
    list.value = data.list
    total.value = data.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.status = ''
  query.form = ''
  load(1)
}

onMounted(() => load(1))
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
