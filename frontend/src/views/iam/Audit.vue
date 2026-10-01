<template>
  <div class="page-card">
    <div class="filter-bar">
      <el-input v-model="query.username" placeholder="用户名" clearable style="width: 150px" @keyup.enter="load(1)" />
      <el-input v-model="query.action" placeholder="动作，如 LOGIN / EXPORT" clearable style="width: 170px" @keyup.enter="load(1)" />
      <el-date-picker v-model="dateRange" type="datetimerange" range-separator="至" start-placeholder="开始时间"
        end-placeholder="结束时间" value-format="YYYY-MM-DD HH:mm:ss" style="width: 340px" />
      <el-button type="primary" @click="load(1)">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <div class="spacer"></div>
      <el-button :loading="exporting" @click="onExport">导出 CSV</el-button>
    </div>
    <el-table :data="list" v-loading="loading" size="small">
      <el-table-column prop="username" label="用户" width="100" />
      <el-table-column label="动作" width="110">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="resource" label="资源" width="110" />
      <el-table-column prop="method" label="方法" width="70" />
      <el-table-column prop="path" label="路径" min-width="240" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <span :style="{ color: row.statusCode < 400 ? '#67c23a' : '#f56c6c' }">{{ row.statusCode }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="120" />
      <el-table-column label="耗时" width="80">
        <template #default="{ row }">{{ row.costMs }}ms</template>
      </el-table-column>
      <el-table-column label="时间" width="165">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" layout="total, prev, pager, next, sizes" :total="total" :page-size="query.size"
      :page-sizes="[10, 20, 50]" :current-page="query.page" @current-change="load"
      @size-change="(s: number) => { query.size = s; load(1) }" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getAuditLogs, exportAuditLogs, type AuditLog } from '@/api/iam'
import { formatTime, downloadBlob } from '@/utils/format'

const list = ref<AuditLog[]>([])
const total = ref(0)
const loading = ref(false)
const exporting = ref(false)
const dateRange = ref<[string, string] | null>(null)
const query = reactive({ page: 1, size: 20, username: '', action: '' })

async function load(page = query.page) {
  query.page = page
  loading.value = true
  try {
    const data = await getAuditLogs({
      page: query.page,
      size: query.size,
      username: query.username || undefined,
      action: query.action || undefined,
      from: dateRange.value?.[0],
      to: dateRange.value?.[1]
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
  query.username = ''
  query.action = ''
  dateRange.value = null
  load(1)
}

async function onExport() {
  exporting.value = true
  try {
    const blob = await exportAuditLogs()
    downloadBlob(blob, `audit-logs-${Date.now()}.csv`)
  } catch {
    // 拦截器已提示
  } finally {
    exporting.value = false
  }
}

onMounted(() => load(1))
</script>

<style scoped>
.spacer {
  flex: 1;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
