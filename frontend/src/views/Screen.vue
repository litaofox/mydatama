<template>
  <div class="screen-page">
    <header class="screen-header">
      <div class="header-left">
        <el-button link class="back-link" @click="$router.push('/processing/upload')">进入工作台 →</el-button>
      </div>
      <h1 class="screen-title">mydatama 数据中台 · 运营全景</h1>
      <div class="header-right">
        <span class="time">{{ now }}</span>
        <span class="user">{{ auth.user?.realName || auth.user?.username }}</span>
      </div>
    </header>

    <div class="stat-row">
      <div class="stat-card" v-for="card in statCards" :key="card.label">
        <div class="stat-value">{{ card.value }}</div>
        <div class="stat-label">{{ card.label }}</div>
      </div>
    </div>

    <div class="grid">
      <div class="panel panel-trend">
        <div class="panel-title">近 7 日上传 / 就绪趋势</div>
        <div ref="trendRef" class="chart chart-trend"></div>
      </div>
      <div class="panel panel-dist">
        <div class="panel-title">模态分布</div>
        <div ref="modalityRef" class="chart chart-pie"></div>
        <div class="panel-title">业务域分布</div>
        <div ref="domainRef" class="chart chart-pie"></div>
      </div>
      <div class="panel panel-product">
        <div class="panel-title">数据产品</div>
        <template v-if="product && product.totalProducts > 0">
          <div class="prod-stats">
            <div class="prod-stat">
              <div class="prod-num">{{ product.totalProducts }}</div>
              <div class="prod-label">产品总数</div>
            </div>
            <div class="prod-stat">
              <div class="prod-num listed">{{ product.listedProducts }}</div>
              <div class="prod-label">已挂牌</div>
            </div>
            <div class="prod-stat">
              <div class="prod-num blocked">{{ product.blockedProducts }}</div>
              <div class="prod-label">合规拦阻</div>
            </div>
            <div class="prod-stat">
              <div class="prod-num pass">{{ product.compliancePassRate }}%</div>
              <div class="prod-label">合规通过率</div>
            </div>
          </div>
          <div ref="formRef" class="chart chart-ring"></div>
          <div class="latest-list">
            <div v-for="item in product.latest" :key="item.code" class="latest-item">
              <span class="latest-name" :class="{ listed: item.status === 'LISTED' }">{{ item.name }}</span>
              <span class="latest-status" :class="item.status.toLowerCase()">{{ productStatusText(item.status) }}</span>
            </div>
          </div>
        </template>
        <div v-else class="empty-hint">暂无产品，待加工</div>
      </div>
      <div class="panel panel-ops">
        <div class="panel-title">最近操作</div>
        <div class="ops-list">
          <div v-for="(op, idx) in opsList" :key="idx" class="ops-item">
            <span class="ops-user">{{ op.username }}</span>
            <span class="ops-action">{{ op.action }}</span>
            <span class="ops-path" :title="op.path">{{ op.path }}</span>
            <span class="ops-time">{{ shortTime(op.createdAt) }}</span>
          </div>
          <div v-if="!opsList.length" class="empty-hint">暂无操作记录</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import { getOverview, getTrend, getDistribution, getOps, getScreenProduct, type ScreenOverview, type ScreenProduct } from '@/api/screen'
import { useAuthStore } from '@/store/auth'
import { productStatusText } from '@/utils/format'

const auth = useAuthStore()

const overview = ref<ScreenOverview | null>(null)
const product = ref<ScreenProduct | null>(null)
const opsList = ref<{ username: string; action: string; path: string; createdAt: string }[]>([])

const trendRef = ref<HTMLElement>()
const modalityRef = ref<HTMLElement>()
const domainRef = ref<HTMLElement>()
const formRef = ref<HTMLElement>()

const trendChart = shallowRef<echarts.ECharts>()
const modalityChart = shallowRef<echarts.ECharts>()
const domainChart = shallowRef<echarts.ECharts>()
const formChart = shallowRef<echarts.ECharts>()

const now = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'))
let clockTimer: ReturnType<typeof setInterval> | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null

const statCards = computed(() => [
  { label: '资产总数', value: overview.value?.assetCount ?? '-' },
  { label: '文件总数', value: overview.value?.fileCount ?? '-' },
  { label: '数据集', value: overview.value?.datasetCount ?? '-' },
  { label: '数据产品', value: overview.value?.productCount ?? '-' },
  { label: '平均质量分', value: overview.value?.avgQuality != null ? Number(overview.value.avgQuality).toFixed(2) : '-' },
  { label: '今日上传', value: overview.value?.todayUploads ?? '-' }
])

const CHART_TEXT = '#cfe3ff'

function initCharts() {
  if (trendRef.value && !trendChart.value) trendChart.value = echarts.init(trendRef.value)
  if (modalityRef.value && !modalityChart.value) modalityChart.value = echarts.init(modalityRef.value)
  if (domainRef.value && !domainChart.value) domainChart.value = echarts.init(domainRef.value)
  if (formRef.value && !formChart.value) formChart.value = echarts.init(formRef.value)
}

async function loadAll() {
  const [ov, trend, dist, ops, prod] = await Promise.all([
    getOverview().catch(() => null),
    getTrend(7).catch(() => null),
    getDistribution().catch(() => null),
    getOps().catch(() => null),
    getScreenProduct().catch(() => null)
  ])
  if (ov) overview.value = ov
  if (ops) opsList.value = ops.list || []
  if (prod) {
    product.value = prod
  }

  initCharts()

  if (trend && trendChart.value) {
    trendChart.value.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['上传数', '就绪资产'], textStyle: { color: CHART_TEXT } },
      grid: { left: 40, right: 16, top: 36, bottom: 24 },
      xAxis: { type: 'category', data: trend.dates, axisLabel: { color: CHART_TEXT } },
      yAxis: { type: 'value', axisLabel: { color: CHART_TEXT }, splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } } },
      series: [
        { name: '上传数', type: 'line', smooth: true, data: trend.uploads, areaStyle: { opacity: 0.25 }, itemStyle: { color: '#4fc3f7' } },
        { name: '就绪资产', type: 'line', smooth: true, data: trend.assetsReady, areaStyle: { opacity: 0.25 }, itemStyle: { color: '#69f0ae' } }
      ]
    })
  }

  const pieBase = {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: CHART_TEXT, fontSize: 11 }, itemWidth: 12, itemHeight: 8 },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '44%'],
      label: { color: CHART_TEXT, fontSize: 11 },
      data: [] as any[]
    }]
  }
  if (dist && modalityChart.value) {
    modalityChart.value.setOption({ ...pieBase, series: [{ ...pieBase.series[0], data: dist.modalityDist }] })
  }
  if (dist && domainChart.value) {
    domainChart.value.setOption({ ...pieBase, series: [{ ...pieBase.series[0], data: dist.domainDist }] })
  }

  if (prod && formChart.value && prod.totalProducts > 0) {
    formChart.value.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { color: CHART_TEXT, fontSize: 11 }, itemWidth: 12, itemHeight: 8 },
      series: [{
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '44%'],
        label: { show: false },
        data: prod.formDistribution.map((f) => ({ name: productFormName(f.form), value: f.count }))
      }]
    })
  }
}

function productFormName(form: string) {
  const map: Record<string, string> = { DATA_PACKAGE: '数据包', API_SERVICE: 'API服务', REPORT: '报告' }
  return map[form] || form
}

function shortTime(t: string) {
  const d = dayjs(t)
  return d.isValid() ? d.format('MM-DD HH:mm') : t
}

function onResize() {
  trendChart.value?.resize()
  modalityChart.value?.resize()
  domainChart.value?.resize()
  formChart.value?.resize()
}

onMounted(() => {
  loadAll()
  clockTimer = setInterval(() => {
    now.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
  }, 1000)
  pollTimer = setInterval(loadAll, 10000)
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  if (pollTimer) clearInterval(pollTimer)
  window.removeEventListener('resize', onResize)
  trendChart.value?.dispose()
  modalityChart.value?.dispose()
  domainChart.value?.dispose()
  formChart.value?.dispose()
})
</script>

<style scoped>
.screen-page {
  min-height: 100%;
  background: radial-gradient(ellipse at top, #0d2247 0%, #060d1f 70%);
  color: #e6f0ff;
  padding: 0 20px 20px;
}
.screen-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
}
.screen-title {
  margin: 0;
  font-size: 24px;
  letter-spacing: 4px;
  background: linear-gradient(90deg, #4fc3f7, #69f0ae);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.header-left,
.header-right {
  width: 220px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #8ea9d0;
}
.header-right {
  justify-content: flex-end;
}
.back-link {
  color: #8ea9d0;
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}
.stat-card {
  background: linear-gradient(160deg, rgba(36, 84, 158, 0.35), rgba(13, 34, 71, 0.6));
  border: 1px solid rgba(79, 195, 247, 0.25);
  border-radius: 10px;
  padding: 16px;
  text-align: center;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #4fc3f7;
}
.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: #8ea9d0;
}
.grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1.2fr;
  grid-template-rows: auto auto;
  gap: 14px;
}
.panel {
  background: rgba(13, 34, 71, 0.55);
  border: 1px solid rgba(79, 195, 247, 0.18);
  border-radius: 10px;
  padding: 14px;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #cfe3ff;
  margin-bottom: 8px;
  border-left: 3px solid #4fc3f7;
  padding-left: 8px;
}
.panel-trend {
  grid-column: 1 / 2;
  grid-row: 1 / 3;
}
.panel-dist {
  grid-column: 2 / 3;
  grid-row: 1 / 3;
}
.panel-product {
  grid-column: 3 / 4;
}
.panel-ops {
  grid-column: 3 / 4;
}
.chart {
  width: 100%;
}
.chart-trend {
  height: 460px;
}
.chart-pie {
  height: 210px;
}
.chart-ring {
  height: 170px;
}
.prod-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 8px;
}
.prod-stat {
  text-align: center;
}
.prod-num {
  font-size: 22px;
  font-weight: 700;
  color: #4fc3f7;
}
.prod-num.listed {
  color: #69f0ae;
}
.prod-num.blocked {
  color: #ff8a80;
}
.prod-num.pass {
  color: #ffd54f;
}
.prod-label {
  font-size: 12px;
  color: #8ea9d0;
  margin-top: 2px;
}
.latest-list {
  margin-top: 6px;
  max-height: 130px;
  overflow-y: auto;
}
.latest-item {
  display: flex;
  justify-content: space-between;
  padding: 5px 4px;
  font-size: 12px;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.08);
}
.latest-name {
  color: #cfe3ff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.latest-name.listed {
  color: #69f0ae;
}
.latest-status {
  color: #8ea9d0;
  flex-shrink: 0;
  margin-left: 8px;
}
.latest-status.listed {
  color: #69f0ae;
}
.latest-status.blocked {
  color: #ff8a80;
}
.ops-list {
  max-height: 240px;
  overflow-y: auto;
}
.ops-item {
  display: flex;
  gap: 8px;
  padding: 6px 4px;
  font-size: 12px;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.08);
  align-items: center;
}
.ops-user {
  color: #4fc3f7;
  flex-shrink: 0;
}
.ops-action {
  color: #ffd54f;
  flex-shrink: 0;
}
.ops-path {
  color: #8ea9d0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.ops-time {
  color: #5c6f92;
  flex-shrink: 0;
}
.empty-hint {
  text-align: center;
  color: #5c6f92;
  padding: 30px 0;
  font-size: 13px;
}
</style>
