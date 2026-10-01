<template>
  <div v-loading="loading">
    <div class="page-card" v-if="asset">
      <div class="head">
        <h3 class="card-title">{{ asset.name }}</h3>
        <div>
          <el-button size="small" @click="$router.push(`/governance/lineage/${asset.id}`)">查看血缘图</el-button>
          <el-button size="small" @click="$router.push('/governance/assets')">返回目录</el-button>
        </div>
      </div>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="资产类型">{{ asset.assetType }}</el-descriptions-item>
        <el-descriptions-item label="模态">{{ modalityText(asset.modality) }}</el-descriptions-item>
        <el-descriptions-item label="业务域">{{ asset.bizDomain || '-' }}</el-descriptions-item>
        <el-descriptions-item label="密级">
          <el-tag size="small" :type="secretLevelTagType(asset.secretLevel)">{{ secretLevelText(asset.secretLevel) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="质量分">
          <el-progress
            v-if="asset.qualityScore != null"
            :percentage="Math.round(asset.qualityScore * 100)"
            :color="asset.qualityScore >= 0.85 ? '#67c23a' : '#e6a23c'"
            :stroke-width="10"
          />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">{{ asset.status }}</el-descriptions-item>
        <el-descriptions-item label="责任部门">{{ asset.ownerDept || '-' }}</el-descriptions-item>
        <el-descriptions-item label="登记时间">{{ formatTime(asset.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="存储引用" :span="4">{{ asset.storageRef || '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="page-card" v-if="asset">
      <h3 class="card-title">字段结构</h3>
      <el-table :data="asset.columns" size="small" border v-if="asset.columns?.length">
        <el-table-column prop="colName" label="列名" min-width="160" />
        <el-table-column prop="dataType" label="数据类型" width="140" />
        <el-table-column label="是否敏感" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.sensitive ? 'danger' : 'info'">{{ row.sensitive ? '敏感' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="脱敏策略" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.maskStrategy" size="small" type="warning">{{ row.maskStrategy }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="非结构化资产或无字段信息" :image-size="60" />
    </div>

    <div class="page-card" v-if="asset">
      <h3 class="card-title">标签</h3>
      <template v-if="asset.tags?.length">
        <el-tag v-for="(tag, idx) in asset.tags" :key="idx" class="tag-item" effect="plain">
          {{ tagText(tag) }}
        </el-tag>
      </template>
      <el-empty v-else description="暂无标签" :image-size="60" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getAssetDetail, type AssetDetail } from '@/api/governance'
import { modalityText, secretLevelText, secretLevelTagType, formatTime } from '@/utils/format'

const route = useRoute()
const asset = ref<AssetDetail | null>(null)
const loading = ref(false)

function tagText(tag: any) {
  if (typeof tag === 'string') return tag
  return tag.name || tag.tagName || JSON.stringify(tag)
}

async function load() {
  loading.value = true
  try {
    asset.value = await getAssetDetail(route.params.id as string)
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
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
.tag-item {
  margin-right: 8px;
  margin-bottom: 8px;
}
</style>
