<template>
  <div v-loading="loading">
    <div class="page-card" v-if="file">
      <div class="head">
        <h3 class="card-title">{{ file.name }}</h3>
        <el-tag :type="job?.status === 'FAILED' ? 'danger' : job?.status === 'SUCCESS' ? 'success' : 'primary'">
          {{ fileStatusText(job?.status || file.status) }}
        </el-tag>
      </div>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="模态">{{ modalityText(file.modality) }}</el-descriptions-item>
        <el-descriptions-item label="业务域">{{ file.bizDomain || '-' }}</el-descriptions-item>
        <el-descriptions-item label="大小">{{ formatBytes(file.sizeBytes) }}</el-descriptions-item>
        <el-descriptions-item label="上传时间">{{ formatTime(file.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="原始路径" :span="2">{{ file.storagePath || '-' }}</el-descriptions-item>
        <el-descriptions-item label="成品路径" :span="2">{{ file.processedPath || '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="page-card" v-if="job">
      <h3 class="card-title">五阶段处理进度</h3>
      <StageTimeline :stage="job.stage" :status="job.status" style="margin-bottom: 44px" />
      <div class="job-meta">
        <span>当前阶段：<b>{{ stageText(job.stage) }}</b></span>
        <span>重试次数：{{ job.retryCount }}</span>
        <span>更新时间：{{ formatTime(job.updatedAt) }}</span>
        <el-button
          v-if="job.status === 'FAILED'"
          type="danger"
          size="small"
          :loading="retrying"
          @click="onRetry"
        >失败重试</el-button>
      </div>
      <el-alert v-if="job.status === 'FAILED' && job.error" type="error" :title="job.error" class="error-alert" :closable="false" />
    </div>

    <div class="page-card" v-if="file">
      <h3 class="card-title">质检指标</h3>
      <el-table :data="metricRows" size="small" border v-if="metricRows.length">
        <el-table-column prop="key" label="指标" width="260" />
        <el-table-column prop="value" label="值" />
      </el-table>
      <el-empty v-else description="暂无质检指标" :image-size="60" />
    </div>

    <div class="page-card" v-if="file">
      <div class="head">
        <h3 class="card-title">脱敏列</h3>
        <el-button type="warning" size="small" @click="repairVisible = true">一键修复（列名映射）</el-button>
      </div>
      <template v-if="maskColumns.length">
        <el-tag v-for="col in maskColumns" :key="col" type="warning" class="mask-tag">{{ col }}</el-tag>
      </template>
      <el-empty v-else description="无脱敏列" :image-size="60" />
      <div class="ops-row">
        <el-button size="small" @click="onDownload('raw')">下载原始文件</el-button>
        <el-button size="small" type="primary" @click="onDownload('processed')">下载成品文件</el-button>
        <el-button size="small" type="success" plain @click="$router.push(`/processing/annotation/${file.id}`)">前往人工标注</el-button>
      </div>
    </div>

    <el-dialog v-model="repairVisible" title="一键修复 — 字段映射" width="560px">
      <p class="text-muted">将质检发现的乱序/异常列名映射为规范列名，提交后自动重跑流水线。</p>
      <div v-for="(row, idx) in repairRows" :key="idx" class="repair-row">
        <el-input v-model="row.from" placeholder="乱序列名，如 tel_num" />
        <span class="arrow">→</span>
        <el-input v-model="row.to" placeholder="修正名，如 phone" />
        <el-button link type="danger" @click="repairRows.splice(idx, 1)">删除</el-button>
      </div>
      <el-button link type="primary" @click="repairRows.push({ from: '', to: '' })">+ 添加映射</el-button>
      <template #footer>
        <el-button @click="repairVisible = false">取消</el-button>
        <el-button type="primary" :loading="repairing" @click="onRepair">提交修复并重跑</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import StageTimeline from '@/components/StageTimeline.vue'
import { getFileDetail, getFileJob, repairFile, retryJob, downloadFile, type ProcFileDetail, type ProcJob } from '@/api/processing'
import { modalityText, formatBytes, formatTime, fileStatusText, downloadBlob } from '@/utils/format'

const route = useRoute()
const fileId = route.params.id as string

const file = ref<ProcFileDetail | null>(null)
const job = ref<ProcJob | null>(null)
const loading = ref(false)
const retrying = ref(false)
const repairing = ref(false)
const repairVisible = ref(false)
const repairRows = ref<{ from: string; to: string }[]>([{ from: '', to: '' }])

let pollTimer: ReturnType<typeof setInterval> | null = null

const maskColumns = computed<string[]>(() => job.value?.maskColumns || file.value?.maskColumns || [])

const metricRows = computed(() => {
  const metrics = job.value?.metrics || file.value?.qualityMetrics || {}
  return Object.entries(metrics).map(([key, value]) => ({
    key,
    value: typeof value === 'object' ? JSON.stringify(value) : String(value)
  }))
})

function stageText(stage?: string) {
  const map: Record<string, string> = {
    COLLECT_VALIDATE: '采集校验',
    CLEAN: '清洗',
    STANDARDIZE: '标准化',
    MASK: '脱敏',
    ANNOTATE_REGISTER: '标注登记'
  }
  return (stage && map[stage]) || stage || '-'
}

async function load() {
  loading.value = true
  try {
    const [f, j] = await Promise.all([
      getFileDetail(fileId).catch(() => null),
      getFileJob(fileId).catch(() => null)
    ])
    if (f) file.value = f
    if (j) job.value = j
    if (j && (j.status === 'PENDING' || j.status === 'RUNNING')) {
      startPolling()
    } else {
      stopPolling()
    }
  } finally {
    loading.value = false
  }
}

function startPolling() {
  if (!pollTimer) {
    pollTimer = setInterval(async () => {
      const j = await getFileJob(fileId).catch(() => null)
      if (j) {
        job.value = j
        if (j.status !== 'PENDING' && j.status !== 'RUNNING') {
          stopPolling()
          load()
        }
      }
    }, 3000)
  }
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function onRetry() {
  if (!job.value) return
  retrying.value = true
  try {
    await retryJob(job.value.jobId)
    ElMessage.success('已重新入队')
    load()
  } catch {
    // 拦截器已提示
  } finally {
    retrying.value = false
  }
}

async function onRepair() {
  const mapping: Record<string, string> = {}
  for (const r of repairRows.value) {
    if (r.from.trim() && r.to.trim()) mapping[r.from.trim()] = r.to.trim()
  }
  if (!Object.keys(mapping).length) {
    ElMessage.warning('请至少填写一条有效映射')
    return
  }
  repairing.value = true
  try {
    await repairFile(fileId, mapping)
    ElMessage.success('修复已提交，流水线重新执行中')
    repairVisible.value = false
    load()
  } catch {
    // 拦截器已提示
  } finally {
    repairing.value = false
  }
}

async function onDownload(variant: 'raw' | 'processed') {
  if (!file.value) return
  try {
    const blob = await downloadFile(fileId, variant)
    downloadBlob(blob, file.value.name)
  } catch {
    // 拦截器已提示
  }
}

onMounted(load)
onBeforeUnmount(stopPolling)
</script>

<style scoped>
.card-title {
  margin: 0;
  font-size: 15px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.job-meta {
  display: flex;
  align-items: center;
  gap: 24px;
  font-size: 13px;
  color: #606266;
}
.error-alert {
  margin-top: 12px;
}
.mask-tag {
  margin-right: 8px;
  margin-bottom: 8px;
}
.ops-row {
  margin-top: 16px;
}
.repair-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.arrow {
  color: #909399;
}
</style>
