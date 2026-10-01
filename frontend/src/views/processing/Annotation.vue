<template>
  <div v-loading="loading">
    <div class="page-card" v-if="file">
      <div class="head">
        <h3 class="card-title">人工标注 — {{ file.name }}</h3>
        <el-button @click="$router.push(`/processing/files/${file.id}`)">返回文件详情</el-button>
      </div>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="模态">{{ modalityText(file.modality) }}</el-descriptions-item>
        <el-descriptions-item label="业务域">{{ file.bizDomain || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ fileStatusText(file.status) }}</el-descriptions-item>
        <el-descriptions-item label="大小">{{ formatBytes(file.sizeBytes) }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="page-card" v-if="file && file.modality === 'IMAGE'">
      <h3 class="card-title">图片预览（缩略图）</h3>
      <el-image :src="thumbUrl" fit="contain" class="thumb" :preview-src-list="[thumbUrl]" preview-teleported>
        <template #error>
          <div class="thumb-error">缩略图不可用</div>
        </template>
      </el-image>
    </div>

    <div class="page-card">
      <h3 class="card-title">标注信息（列名确认 / 修正）</h3>
      <p class="text-muted">对质检识别的乱序列名进行人工确认，提交后触发流水线重跑。</p>
      <div v-for="(row, idx) in mappingRows" :key="idx" class="mapping-row">
        <el-input v-model="row.from" placeholder="原始列名" />
        <span class="arrow">→</span>
        <el-input v-model="row.to" placeholder="标注（修正）列名" />
        <el-button link type="danger" @click="mappingRows.splice(idx, 1)">删除</el-button>
      </div>
      <el-button link type="primary" @click="mappingRows.push({ from: '', to: '' })">+ 添加标注</el-button>
      <div class="submit-row">
        <el-button type="primary" :loading="submitting" @click="onSubmit">提交标注</el-button>
      </div>
    </div>

    <div class="page-card" v-if="maskColumns.length">
      <h3 class="card-title">已脱敏列</h3>
      <el-tag v-for="col in maskColumns" :key="col" type="warning" class="mask-tag">{{ col }}</el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getFileDetail, repairFile, getThumbUrl, type ProcFileDetail } from '@/api/processing'
import { modalityText, formatBytes, fileStatusText } from '@/utils/format'

const route = useRoute()
const fileId = route.params.fileId as string

const file = ref<ProcFileDetail | null>(null)
const loading = ref(false)
const submitting = ref(false)
const mappingRows = ref<{ from: string; to: string }[]>([{ from: '', to: '' }])

const thumbUrl = computed(() => getThumbUrl(fileId))
const maskColumns = computed(() => file.value?.maskColumns || [])

async function load() {
  loading.value = true
  try {
    file.value = await getFileDetail(fileId)
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  const mapping: Record<string, string> = {}
  for (const r of mappingRows.value) {
    if (r.from.trim() && r.to.trim()) mapping[r.from.trim()] = r.to.trim()
  }
  if (!Object.keys(mapping).length) {
    ElMessage.warning('请至少填写一条有效标注')
    return
  }
  submitting.value = true
  try {
    await repairFile(fileId, mapping)
    ElMessage.success('标注已提交')
    load()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-title {
  margin: 0 0 16px;
  font-size: 15px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.head .card-title {
  margin-bottom: 0;
}
.thumb {
  max-width: 480px;
  max-height: 320px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}
.thumb-error {
  width: 480px;
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
}
.mapping-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.arrow {
  color: #909399;
}
.submit-row {
  margin-top: 16px;
}
.mask-tag {
  margin-right: 8px;
}
</style>
