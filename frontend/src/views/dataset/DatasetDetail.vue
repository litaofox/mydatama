<template>
  <div v-loading="loading">
    <div class="page-card">
      <div class="head">
        <h3 class="card-title">数据集 #{{ datasetId }} — 版本管理</h3>
        <div>
          <el-button type="primary" size="small" @click="versionVisible = true">生成新版本</el-button>
          <el-button size="small" :disabled="selectedVersions.length !== 2" @click="onCompare">版本对比</el-button>
          <el-button size="small" @click="$router.push('/dataset/list')">返回列表</el-button>
        </div>
      </div>
      <el-table :data="versions" @selection-change="(rows: any[]) => (selectedVersions = rows)">
        <el-table-column type="selection" width="45" />
        <el-table-column label="版本号" width="100">
          <template #default="{ row }">
            <el-tag effect="dark">v{{ row.versionNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="itemCount" label="资产数" width="90" />
        <el-table-column label="质量总分" width="100">
          <template #default="{ row }">
            <span :style="{ color: overallScore(row) >= 0.85 ? '#67c23a' : '#e6a23c', fontWeight: 600 }">
              {{ overallScore(row).toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="changeNote" label="变更说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="创建时间" width="165">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewVersion(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="versionVisible" title="生成新版本" width="440px">
      <el-input v-model="changeNote" type="textarea" :rows="3" placeholder="变更说明，如：追加 0902 数据" />
      <template #footer>
        <el-button @click="versionVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="onCreateVersion">生成</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="`版本 v${currentVersion?.versionNo} 详情`" size="620px">
      <template v-if="currentVersion">
        <h4>三维质检报告</h4>
        <div ref="radarRef" class="radar-chart"></div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="完整性">{{ scoreText('completeness') }}</el-descriptions-item>
          <el-descriptions-item label="一致性">{{ scoreText('consistency') }}</el-descriptions-item>
          <el-descriptions-item label="准确性">{{ scoreText('accuracy') }}</el-descriptions-item>
          <el-descriptions-item label="综合得分">{{ scoreText('overall_score') }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin-top: 20px">资产清单（{{ currentVersion.items?.length || 0 }}）</h4>
        <el-table :data="currentVersion.items" size="small" max-height="360">
          <el-table-column prop="assetId" label="资产ID" width="90" />
          <el-table-column label="资产名称" min-width="180">
            <template #default="{ row }">{{ row.assetSnapshot?.name || '-' }}</template>
          </el-table-column>
          <el-table-column label="模态" width="90">
            <template #default="{ row }">{{ modalityText(row.assetSnapshot?.modality) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="compareVisible" title="版本对比" width="720px">
      <template v-if="compareResult">
        <el-row :gutter="16">
          <el-col :span="12">
            <h4 class="added">新增（{{ compareResult.added.length }}）</h4>
            <div v-for="(item, idx) in compareResult.added" :key="idx" class="compare-item">
              {{ compareItemText(item) }}
            </div>
            <el-empty v-if="!compareResult.added.length" description="无新增" :image-size="50" />
          </el-col>
          <el-col :span="12">
            <h4 class="removed">移除（{{ compareResult.removed.length }}）</h4>
            <div v-for="(item, idx) in compareResult.removed" :key="idx" class="compare-item">
              {{ compareItemText(item) }}
            </div>
            <el-empty v-if="!compareResult.removed.length" description="无移除" :image-size="50" />
          </el-col>
        </el-row>
        <h4 style="margin-top: 16px">指标差异</h4>
        <el-table :data="metricsDiffRows" size="small" border v-if="metricsDiffRows.length">
          <el-table-column prop="key" label="指标" width="220" />
          <el-table-column prop="value" label="差异" />
        </el-table>
        <el-empty v-else description="无指标差异" :image-size="50" />
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, shallowRef } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  getVersions, createVersion, getVersionDetail, compareVersions,
  type DatasetVersion, type DatasetVersionDetail, type VersionCompareResult
} from '@/api/dataset'
import { formatTime, modalityText } from '@/utils/format'

const route = useRoute()
const datasetId = route.params.id as string

const versions = ref<DatasetVersion[]>([])
const loading = ref(false)
const creating = ref(false)
const versionVisible = ref(false)
const changeNote = ref('')
const selectedVersions = ref<DatasetVersion[]>([])

const detailVisible = ref(false)
const currentVersion = ref<DatasetVersionDetail | null>(null)
const radarRef = ref<HTMLElement>()
const radarChart = shallowRef<echarts.ECharts>()

const compareVisible = ref(false)
const compareResult = ref<VersionCompareResult | null>(null)

function overallScore(row: DatasetVersion): number {
  return Number(row.qualityReport?.overall_score ?? 0)
}

function scoreText(key: string) {
  const v = currentVersion.value?.qualityReport?.[key]
  return v != null ? Number(v).toFixed(2) : '-'
}

const metricsDiffRows = computed(() => {
  const diff = compareResult.value?.metricsDiff || {}
  return Object.entries(diff).map(([key, value]) => ({
    key,
    value: typeof value === 'object' ? JSON.stringify(value) : String(value)
  }))
})

function compareItemText(item: any) {
  if (typeof item === 'string') return item
  return item.assetSnapshot?.name || item.name || `资产 #${item.assetId ?? item.id ?? '?'}`
}

async function load() {
  loading.value = true
  try {
    versions.value = await getVersions(datasetId)
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function onCreateVersion() {
  creating.value = true
  try {
    const result = await createVersion(datasetId, changeNote.value)
    ElMessage.success(`版本 v${result.versionNo} 生成成功，含 ${result.itemCount} 个资产`)
    versionVisible.value = false
    changeNote.value = ''
    load()
  } catch {
    // 拦截器已提示
  } finally {
    creating.value = false
  }
}

async function viewVersion(row: DatasetVersion) {
  try {
    currentVersion.value = await getVersionDetail(row.id)
    detailVisible.value = true
    await nextTick()
    renderRadar()
  } catch {
    // 拦截器已提示
  }
}

function renderRadar() {
  const report = currentVersion.value?.qualityReport
  if (!radarRef.value || !report) return
  if (!radarChart.value) radarChart.value = echarts.init(radarRef.value)
  radarChart.value.setOption({
    radar: {
      indicator: [
        { name: '完整性', max: 1 },
        { name: '一致性', max: 1 },
        { name: '准确性', max: 1 }
      ],
      radius: '65%'
    },
    series: [{
      type: 'radar',
      data: [{
        value: [
          Number(report.completeness ?? 0),
          Number(report.consistency ?? 0),
          Number(report.accuracy ?? 0)
        ],
        name: '三维质检得分',
        areaStyle: { opacity: 0.3 },
        itemStyle: { color: '#409eff' }
      }]
    }]
  }, true)
}

async function onCompare() {
  if (selectedVersions.value.length !== 2) return
  const [a, b] = selectedVersions.value
  try {
    compareResult.value = await compareVersions(a.id, b.id)
    compareVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

onMounted(load)
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
.radar-chart {
  width: 100%;
  height: 260px;
}
.added {
  color: #67c23a;
}
.removed {
  color: #f56c6c;
}
.compare-item {
  font-size: 13px;
  padding: 4px 0;
  border-bottom: 1px dashed #ebeef5;
}
</style>
