<template>
  <div>
    <div class="page-card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="文件名关键词" clearable style="width: 220px" @keyup.enter="load(1)" />
        <el-select v-model="query.modality" placeholder="模态" clearable style="width: 140px">
          <el-option label="结构化" value="STRUCTURED" />
          <el-option label="文本" value="TEXT" />
          <el-option label="图像" value="IMAGE" />
          <el-option label="视频" value="VIDEO" />
        </el-select>
        <el-button type="primary" @click="load(1)">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
      <el-table :data="list" v-loading="loading">
        <el-table-column prop="name" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column label="模态" width="100">
          <template #default="{ row }">{{ modalityText(row.modality) }}</template>
        </el-table-column>
        <el-table-column prop="bizDomain" label="业务域" width="120">
          <template #default="{ row }">{{ row.bizDomain || '-' }}</template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ fileStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/processing/files/${row.id}`)">详情</el-button>
            <el-button link type="primary" @click="$router.push(`/processing/annotation/${row.id}`)">标注</el-button>
            <el-button link type="primary" @click="onDownload(row, 'processed')">下载成品</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="query.size"
        :current-page="query.page"
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getFiles, downloadFile, type ProcFile } from '@/api/processing'
import { modalityText, formatBytes, formatTime, fileStatusText, downloadBlob } from '@/utils/format'

const list = ref<ProcFile[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 10, keyword: '', modality: '' })

function statusType(status: string) {
  if (status === 'SUCCESS' || status === 'READY') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'primary'
}

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await getFiles({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      modality: query.modality || undefined
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
  load(1)
}

async function onDownload(row: ProcFile, variant: 'raw' | 'processed') {
  try {
    const blob = await downloadFile(row.id, variant)
    downloadBlob(blob, row.name)
  } catch {
    // 拦截器已提示
  }
}

onMounted(() => load(1))
</script>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
