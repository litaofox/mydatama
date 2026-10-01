<template>
  <div class="page-card">
    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="资产名称关键词" clearable style="width: 200px" @keyup.enter="load(1)" />
      <el-select v-model="query.modality" placeholder="模态" clearable style="width: 130px">
        <el-option label="结构化" value="STRUCTURED" />
        <el-option label="文本" value="TEXT" />
        <el-option label="图像" value="IMAGE" />
        <el-option label="视频" value="VIDEO" />
      </el-select>
      <el-select v-model="query.assetType" placeholder="资产类型" clearable style="width: 140px">
        <el-option label="原始文件" value="RAW_FILE" />
        <el-option label="成品文件" value="PROCESSED_FILE" />
        <el-option label="数据集" value="DATASET" />
        <el-option label="产品" value="PRODUCT" />
      </el-select>
      <el-input v-model="query.bizDomain" placeholder="业务域" clearable style="width: 140px" @keyup.enter="load(1)" />
      <el-button type="primary" @click="load(1)">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="name" label="资产名称" min-width="200" show-overflow-tooltip />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ assetTypeText(row.assetType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="模态" width="90">
        <template #default="{ row }">{{ modalityText(row.modality) }}</template>
      </el-table-column>
      <el-table-column prop="bizDomain" label="业务域" width="110">
        <template #default="{ row }">{{ row.bizDomain || '-' }}</template>
      </el-table-column>
      <el-table-column label="密级" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="secretLevelTagType(row.secretLevel)">{{ secretLevelText(row.secretLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="质量分" width="90">
        <template #default="{ row }">
          <span :style="{ color: row.qualityScore >= 0.85 ? '#67c23a' : '#e6a23c' }">
            {{ row.qualityScore != null ? Number(row.qualityScore).toFixed(2) : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'READY' ? 'success' : 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="登记时间" width="165">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/governance/assets/${row.id}`)">详情</el-button>
          <el-button link type="primary" @click="$router.push(`/governance/lineage/${row.id}`)">血缘</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" layout="total, prev, pager, next" :total="total" :page-size="query.size"
      :current-page="query.page" @current-change="load" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getAssets, type Asset } from '@/api/governance'
import { modalityText, secretLevelText, secretLevelTagType, formatTime } from '@/utils/format'

const list = ref<Asset[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 10, keyword: '', modality: '', assetType: '', bizDomain: '' })

function assetTypeText(t: string) {
  const map: Record<string, string> = {
    RAW_FILE: '原始文件',
    PROCESSED_FILE: '成品文件',
    DATASET: '数据集',
    PRODUCT: '产品'
  }
  return map[t] || t
}

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await getAssets({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      modality: query.modality || undefined,
      assetType: query.assetType || undefined,
      bizDomain: query.bizDomain || undefined
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
  query.modality = ''
  query.assetType = ''
  query.bizDomain = ''
  load(1)
}

onMounted(() => load(1))
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
