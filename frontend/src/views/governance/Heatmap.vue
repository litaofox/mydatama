<template>
  <div class="page-card">
    <div class="head">
      <h3 class="card-title">数据热力图（业务域 × 日期）</h3>
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
    </div>
    <div ref="chartRef" class="heatmap-chart" v-loading="loading"></div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import * as echarts from 'echarts'
import { getHeatmap } from '@/api/governance'

const chartRef = ref<HTMLElement>()
const chart = shallowRef<echarts.ECharts>()
const loading = ref(false)
const days = ref(30)

async function load() {
  loading.value = true
  try {
    const data = await getHeatmap(days.value)
    render(data.domains, data.dates, data.data)
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function render(domains: string[], dates: string[], data: [number, number, number][]) {
  if (!chartRef.value) return
  if (!chart.value) chart.value = echarts.init(chartRef.value)
  const max = data.length ? Math.max(...data.map((d) => d[2])) : 1
  chart.value.setOption({
    tooltip: {
      position: 'top',
      formatter: (p: any) => `${domains[p.data[0]]} / ${dates[p.data[1]]}<br/>处理量：${p.data[2]}`
    },
    grid: { left: 110, right: 60, top: 20, bottom: 70 },
    xAxis: { type: 'category', data: domains, splitArea: { show: true }, name: '业务域' },
    yAxis: { type: 'category', data: dates, splitArea: { show: true }, name: '日期' },
    visualMap: {
      min: 0,
      max: max || 1,
      calculable: true,
      orient: 'vertical',
      right: 0,
      top: 'center',
      inRange: { color: ['#f0f7ff', '#4fc3f7', '#1c5bc4'] }
    },
    series: [{
      type: 'heatmap',
      data: data,
      label: { show: false },
      emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.4)' } }
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
.heatmap-chart {
  width: 100%;
  height: 560px;
}
</style>
