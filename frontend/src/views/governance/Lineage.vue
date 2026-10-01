<template>
  <div class="page-card">
    <div class="head">
      <h3 class="card-title">血缘图（资产 #{{ route.params.id }}）</h3>
      <div>
        <el-select v-model="depth" size="small" style="width: 110px" @change="load">
          <el-option :value="1" label="深度 1" />
          <el-option :value="2" label="深度 2" />
          <el-option :value="3" label="深度 3" />
          <el-option :value="5" label="深度 5" />
        </el-select>
        <el-button size="small" style="margin-left: 8px" @click="$router.push('/governance/assets')">返回目录</el-button>
      </div>
    </div>
    <div ref="chartRef" class="lineage-chart" v-loading="loading"></div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { getLineage } from '@/api/governance'

const route = useRoute()
const chartRef = ref<HTMLElement>()
const chart = shallowRef<echarts.ECharts>()
const loading = ref(false)
const depth = ref(3)

const TYPE_COLOR: Record<string, string> = {
  RAW_FILE: '#e6a23c',
  PROCESSED_FILE: '#409eff',
  DATASET: '#67c23a',
  PRODUCT: '#b88230'
}

async function load() {
  loading.value = true
  try {
    const data = await getLineage(route.params.id as string, depth.value)
    render(data.nodes, data.edges)
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function render(nodes: { id: number; name: string; assetType: string; modality: string; level: number }[],
  edges: { from: number; to: number; relType: string }[]) {
  if (!chartRef.value) return
  if (!chart.value) chart.value = echarts.init(chartRef.value)
  const categories = [...new Set(nodes.map((n) => n.assetType))]
  chart.value.setOption({
    tooltip: {
      formatter: (p: any) => {
        if (p.dataType === 'node') {
          return `${p.data.name}<br/>类型：${p.data.assetType}<br/>模态：${p.data.modality || '-'}`
        }
        return p.data.relType || ''
      }
    },
    legend: { data: categories, bottom: 0 },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      force: { repulsion: 400, edgeLength: 120 },
      categories: categories.map((c) => ({ name: c })),
      label: { show: true, position: 'bottom', fontSize: 11 },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 8,
      lineStyle: { color: '#909399', width: 1.5, curveness: 0.1 },
      data: nodes.map((n) => ({
        id: String(n.id),
        name: n.name,
        assetType: n.assetType,
        modality: n.modality,
        category: n.assetType,
        symbolSize: n.assetType === 'PRODUCT' ? 56 : n.assetType === 'DATASET' ? 50 : 42,
        itemStyle: { color: TYPE_COLOR[n.assetType] || '#909399' }
      })),
      links: edges.map((e) => ({
        source: String(e.from),
        target: String(e.to),
        relType: e.relType,
        label: { show: true, formatter: e.relType, fontSize: 10 }
      }))
    }]
  }, true)
}

function onResize() {
  chart.value?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart.value?.dispose()
})
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
.lineage-chart {
  width: 100%;
  height: 560px;
}
</style>
