<template>
  <div class="page-card">
    <div class="filter-bar">
      <h3 class="card-title">数据集列表</h3>
      <el-button type="primary" @click="createVisible = true">构建数据集</el-button>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="scenario" label="场景" width="160" />
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column prop="creator" label="创建人" width="110" />
      <el-table-column label="创建时间" width="165">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$router.push(`/dataset/${row.id}`)">版本管理</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="构建数据集" width="560px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如 驾驶行为样本集" />
        </el-form-item>
        <el-form-item label="场景">
          <el-input v-model="form.scenario" placeholder="如 保险精算 / 驾驶行为分析" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-divider content-position="left">筛选条件（filterCond）</el-divider>
        <el-form-item label="模态">
          <el-select v-model="form.filterCond.modality" clearable style="width: 100%">
            <el-option label="结构化" value="STRUCTURED" />
            <el-option label="文本" value="TEXT" />
            <el-option label="图像" value="IMAGE" />
            <el-option label="视频" value="VIDEO" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务域">
          <el-input v-model="form.filterCond.bizDomain" />
        </el-form-item>
        <el-form-item label="最低质量分">
          <el-input-number v-model="form.filterCond.minQualityScore" :min="0" :max="1" :step="0.05" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最高密级">
          <el-input-number v-model="form.filterCond.secretLevelMax" :min="1" :max="4" style="width: 100%" />
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
import { getDatasets, createDataset, type Dataset } from '@/api/dataset'
import { formatTime } from '@/utils/format'

const list = ref<Dataset[]>([])
const loading = ref(false)
const saving = ref(false)
const createVisible = ref(false)

const form = reactive({
  name: '',
  scenario: '',
  description: '',
  filterCond: {
    modality: '',
    bizDomain: '',
    minQualityScore: 0.85,
    secretLevelMax: 4
  }
})

async function load() {
  loading.value = true
  try {
    list.value = await getDatasets()
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写数据集名称')
    return
  }
  saving.value = true
  try {
    const filterCond: Record<string, any> = {}
    if (form.filterCond.modality) filterCond.modality = form.filterCond.modality
    if (form.filterCond.bizDomain) filterCond.bizDomain = form.filterCond.bizDomain
    if (form.filterCond.minQualityScore != null) filterCond.minQualityScore = form.filterCond.minQualityScore
    if (form.filterCond.secretLevelMax != null) filterCond.secretLevelMax = form.filterCond.secretLevelMax
    await createDataset({
      name: form.name,
      scenario: form.scenario,
      description: form.description,
      filterCond
    })
    ElMessage.success('数据集创建成功')
    createVisible.value = false
    Object.assign(form, {
      name: '', scenario: '', description: '',
      filterCond: { modality: '', bizDomain: '', minQualityScore: 0.85, secretLevelMax: 4 }
    })
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
