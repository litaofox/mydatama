<template>
  <div>
    <div class="page-card">
      <h3 class="card-title">文件上传</h3>
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :on-change="onFileChange"
        :limit="1"
        :on-exceed="onExceed"
        class="upload-area"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或 <em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 CSV / XLSX / JSON / TXT / ZIP（图像、视频），单文件不超过 200MB</div>
        </template>
      </el-upload>
      <div class="actions">
        <el-button type="primary" :disabled="!selectedFile" :loading="uploading" @click="doUpload">开始上传</el-button>
        <el-button @click="reset">清空</el-button>
      </div>
      <el-progress v-if="uploading" :percentage="100" status="active" :stroke-width="6" striped striped-flow class="progress" />
    </div>

    <div class="page-card">
      <h3 class="card-title">最近上传</h3>
      <el-table :data="recentFiles" v-loading="loadingList" size="default">
        <el-table-column prop="name" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column label="模态" width="100">
          <template #default="{ row }">{{ modalityText(row.modality) }}</template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' || row.status === 'READY' ? 'success' : row.status === 'FAILED' ? 'danger' : 'primary'" size="small">
              {{ fileStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/processing/files/${row.id}`)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, type UploadFile, type UploadRawFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { uploadFile, getFiles, type ProcFile } from '@/api/processing'
import { modalityText, formatBytes, formatTime, fileStatusText } from '@/utils/format'

const uploadRef = ref()
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const recentFiles = ref<ProcFile[]>([])
const loadingList = ref(false)

function onFileChange(uploadFile: UploadFile) {
  selectedFile.value = uploadFile.raw || null
}

function onExceed(files: File[]) {
  uploadRef.value?.clearFiles()
  const file = files[0] as UploadRawFile
  uploadRef.value?.handleStart(file)
  selectedFile.value = file
}

async function doUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const result = await uploadFile(selectedFile.value)
    ElMessage.success(`上传成功：${result.name}，已进入五阶段处理队列`)
    reset()
    loadRecent()
  } catch {
    // 拦截器已提示
  } finally {
    uploading.value = false
  }
}

function reset() {
  uploadRef.value?.clearFiles()
  selectedFile.value = null
}

async function loadRecent() {
  loadingList.value = true
  try {
    const data = await getFiles({ page: 1, size: 10 })
    recentFiles.value = data.list
  } catch {
    // ignore
  } finally {
    loadingList.value = false
  }
}

onMounted(loadRecent)
</script>

<style scoped>
.card-title {
  margin: 0 0 16px;
  font-size: 15px;
}
.upload-area {
  max-width: 640px;
}
.actions {
  margin-top: 16px;
}
.progress {
  margin-top: 12px;
  max-width: 640px;
}
</style>
