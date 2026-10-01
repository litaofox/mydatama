<template>
  <div v-loading="loading">
    <template v-if="product">
      <div class="page-card">
        <div class="head">
          <div class="head-left">
            <h3 class="card-title">{{ product.name }}</h3>
            <el-tag :type="productStatusTagType(product.status)" effect="dark">{{ productStatusText(product.status) }}</el-tag>
          </div>
          <el-button size="small" @click="$router.push('/product/list')">返回列表</el-button>
        </div>

        <el-steps :active="stepActive" align-center class="status-steps" :process-status="product.status === 'BLOCKED' ? 'error' : 'process'">
          <el-step title="草稿" />
          <el-step title="已配置" />
          <el-step title="已生成" />
          <el-step :title="product.status === 'BLOCKED' ? '合规拦阻' : '合规通过'" />
          <el-step title="已登记" />
          <el-step title="已挂牌" />
        </el-steps>

        <div class="action-bar">
          <el-button v-if="product.status === 'DRAFT'" type="primary" :loading="acting" @click="doAction('configure')">提交配置</el-button>
          <el-button v-if="product.status === 'CONFIGURED'" type="primary" :loading="acting" @click="doAction('generate')">执行生成</el-button>
          <el-button v-if="product.status === 'GENERATED' || product.status === 'BLOCKED'" type="warning" :loading="acting" @click="doAction('compliance')">
            {{ product.status === 'BLOCKED' ? '重新合规校验' : '发起合规校验' }}
          </el-button>
          <el-button v-if="product.status === 'PASSED'" type="primary" :loading="acting" @click="doAction('register')">登记</el-button>
          <el-button v-if="product.status === 'REGISTERED'" type="success" :loading="acting" @click="doAction('listing')">挂牌</el-button>
          <el-button v-if="canExport" type="primary" plain :loading="exporting" @click="onExport">导出交付物</el-button>
          <el-button v-if="showManualBtn" plain @click="onPreviewManual">说明书预览</el-button>
          <el-button v-if="product.status === 'DRAFT' || product.status === 'BLOCKED'" plain @click="editVisible = true">修改配置</el-button>
        </div>

        <el-alert v-if="product.lastError" type="error" :title="product.lastError" class="error-alert" :closable="false" />

        <el-descriptions :column="4" border size="small" class="desc">
          <el-descriptions-item label="产品编码">{{ product.code }}</el-descriptions-item>
          <el-descriptions-item label="形态">{{ productFormText(product.form) }}</el-descriptions-item>
          <el-descriptions-item label="密级">
            <el-tag size="small" :type="secretLevelTagType(product.secretLevel)">{{ secretLevelText(product.secretLevel) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="分类">{{ product.category || '-' }}</el-descriptions-item>
          <el-descriptions-item label="原料数据集">#{{ product.datasetId }}</el-descriptions-item>
          <el-descriptions-item label="原料版本">v{{ product.datasetVersionNo }}</el-descriptions-item>
          <el-descriptions-item label="定价">{{ pricingText }}</el-descriptions-item>
          <el-descriptions-item label="登记凭证号">{{ product.regNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="挂牌时间">{{ formatTime(product.listedAt) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(product.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ product.description || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="page-card" v-if="complianceItems.length">
        <div class="head">
          <h3 class="card-title">合规校验结果</h3>
          <span class="text-muted" v-if="product.latestCompliance">批次：{{ product.latestCompliance.batchNo }}</span>
        </div>
        <div class="compliance-list">
          <div v-for="item in complianceItems" :key="item.ruleCode" class="compliance-item" :class="{ passed: item.passed, failed: !item.passed }">
            <el-icon class="c-icon"><CircleCheckFilled v-if="item.passed" /><CircleCloseFilled v-else /></el-icon>
            <div class="c-main">
              <div class="c-name">{{ item.ruleName || ruleName(item.ruleCode) }} <span class="c-code">{{ item.ruleCode }}</span></div>
              <div class="c-detail">{{ item.detail }}</div>
            </div>
            <el-tag :type="item.passed ? 'success' : 'danger'" size="small">{{ item.passed ? '通过' : '不通过' }}</el-tag>
          </div>
        </div>
      </div>

      <div class="page-card" v-if="product.manualMeta">
        <h3 class="card-title">说明书四要素</h3>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="来源说明">{{ product.manualMeta.sourceDesc || product.manualMeta.source_desc || '-' }}</el-descriptions-item>
          <el-descriptions-item label="字段说明">{{ product.manualMeta.fieldDesc || product.manualMeta.field_desc || '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新频率">{{ product.manualMeta.updateFreq || product.manualMeta.update_freq || '-' }}</el-descriptions-item>
          <el-descriptions-item label="交付方式">{{ product.manualMeta.deliveryMode || product.manualMeta.delivery_mode || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="page-card">
        <h3 class="card-title">产物清单</h3>
        <el-table :data="product.artifacts" size="small" border v-if="product.artifacts?.length">
          <el-table-column prop="artifactType" label="类型" width="180" />
          <el-table-column prop="filePath" label="文件路径" min-width="260" show-overflow-tooltip />
          <el-table-column label="大小" width="110">
            <template #default="{ row }">{{ formatBytes(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column prop="checksum" label="SHA-256" min-width="180" show-overflow-tooltip />
        </el-table>
        <el-empty v-else description="暂无产物（执行生成后产出）" :image-size="60" />
      </div>

      <el-dialog v-model="editVisible" title="修改配置" width="640px">
        <el-form label-width="110px">
          <el-form-item label="产品名称">
            <el-input v-model="editForm.name" />
          </el-form-item>
          <el-form-item label="密级">
            <el-select v-model="editForm.secretLevel" style="width: 100%">
              <el-option :value="1" label="1 公开" />
              <el-option :value="2" label="2 内部" />
              <el-option :value="3" label="3 秘密" />
              <el-option :value="4" label="4 机密" />
            </el-select>
          </el-form-item>
          <el-form-item label="分类">
            <el-input v-model="editForm.category" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="editForm.description" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="来源说明">
            <el-input v-model="editForm.manualMeta.sourceDesc" />
          </el-form-item>
          <el-form-item label="字段说明">
            <el-input v-model="editForm.manualMeta.fieldDesc" />
          </el-form-item>
          <el-form-item label="更新频率">
            <el-select v-model="editForm.manualMeta.updateFreq" style="width: 100%">
              <el-option value="DAILY" label="每日" />
              <el-option value="WEEKLY" label="每周" />
              <el-option value="MONTHLY" label="每月" />
              <el-option value="ONE_TIME" label="一次性" />
            </el-select>
          </el-form-item>
          <el-form-item label="交付方式">
            <el-input v-model="editForm.manualMeta.deliveryMode" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editVisible = false">取消</el-button>
          <el-button type="primary" :loading="acting" @click="onSaveEdit">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="manualVisible" title="说明书预览" width="860px" top="4vh">
        <iframe v-if="manualBlobUrl" :src="manualBlobUrl" class="manual-frame"></iframe>
        <el-empty v-else description="说明书不可用（需生成后预览）" :image-size="80" />
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import {
  getProductDetail, updateProduct, configureProduct, generateProduct, runCompliance,
  registerProduct, listingProduct, exportProduct, getManualHtml,
  type ProductDetail, type ComplianceItem
} from '@/api/product'
import {
  productFormText, productStatusText, productStatusTagType,
  secretLevelText, secretLevelTagType, formatTime, formatBytes, downloadBlob
} from '@/utils/format'

const route = useRoute()
const productId = route.params.id as string

const product = ref<ProductDetail | null>(null)
const loading = ref(false)
const acting = ref(false)
const exporting = ref(false)
const editVisible = ref(false)
const manualVisible = ref(false)
const manualBlobUrl = ref('')

const editForm = reactive({
  name: '',
  secretLevel: 2,
  category: '',
  description: '',
  manualMeta: { sourceDesc: '', fieldDesc: '', updateFreq: 'MONTHLY', deliveryMode: '' }
})

const STATUS_ORDER = ['DRAFT', 'CONFIGURED', 'GENERATED', 'PASSED', 'REGISTERED', 'LISTED']

const stepActive = computed(() => {
  const s = product.value?.status
  if (!s) return 0
  if (s === 'BLOCKED') return 3
  const idx = STATUS_ORDER.indexOf(s)
  return idx < 0 ? 0 : idx
})

const complianceItems = computed<ComplianceItem[]>(() => product.value?.latestCompliance?.items || [])

const canExport = computed(() => {
  const s = product.value?.status
  return s === 'PASSED' || s === 'REGISTERED' || s === 'LISTED'
})

const showManualBtn = computed(() => {
  const s = product.value?.status
  return s !== 'DRAFT' && s !== 'CONFIGURED'
})

const pricingText = computed(() => {
  const p = product.value
  if (!p?.pricingModel) return '-'
  const model = { PER_CALL: '按次', PER_MONTH: '按月', ONE_TIME: '买断' }[p.pricingModel] || p.pricingModel
  return p.price != null ? `${model} ¥${p.price}` : model
})

function ruleName(code: string) {
  const map: Record<string, string> = {
    SECRET_LEVEL: '密级合规',
    SENSITIVE_MASKED: '敏感列脱敏',
    QUALITY_THRESHOLD: '质量达标',
    MANUAL_COMPLETE: '说明书完整'
  }
  return map[code] || code
}

async function load() {
  loading.value = true
  try {
    product.value = await getProductDetail(productId)
    fillEditForm()
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function fillEditForm() {
  const p = product.value
  if (!p) return
  editForm.name = p.name
  editForm.secretLevel = p.secretLevel
  editForm.category = p.category || ''
  editForm.description = p.description || ''
  editForm.manualMeta = {
    sourceDesc: p.manualMeta?.sourceDesc || p.manualMeta?.source_desc || '',
    fieldDesc: p.manualMeta?.fieldDesc || p.manualMeta?.field_desc || '',
    updateFreq: p.manualMeta?.updateFreq || p.manualMeta?.update_freq || 'MONTHLY',
    deliveryMode: p.manualMeta?.deliveryMode || p.manualMeta?.delivery_mode || ''
  }
}

async function doAction(action: 'configure' | 'generate' | 'compliance' | 'register' | 'listing') {
  acting.value = true
  try {
    if (action === 'configure') {
      await configureProduct(productId)
      ElMessage.success('配置已提交')
    } else if (action === 'generate') {
      await generateProduct(productId)
      ElMessage.success('生成成功')
    } else if (action === 'compliance') {
      const result = await runCompliance(productId)
      if (result.status === 'PASSED') {
        ElMessage.success('合规校验通过')
      } else {
        ElMessage.warning('合规拦阻，请查看失败原因')
      }
    } else if (action === 'register') {
      await registerProduct(productId)
      ElMessage.success('登记成功')
    } else if (action === 'listing') {
      await listingProduct(productId)
      ElMessage.success('挂牌成功，大屏产品板块将在 10s 内更新')
    }
    await load()
  } catch {
    await load()
  } finally {
    acting.value = false
  }
}

async function onSaveEdit() {
  acting.value = true
  try {
    await updateProduct(productId, {
      name: editForm.name,
      secretLevel: editForm.secretLevel,
      category: editForm.category,
      description: editForm.description,
      manualMeta: { ...editForm.manualMeta }
    })
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  } catch {
    // 拦截器已提示
  } finally {
    acting.value = false
  }
}

async function onExport() {
  exporting.value = true
  try {
    const blob = await exportProduct(productId)
    const form = product.value?.form
    const ext = form === 'REPORT' ? 'pdf' : 'zip'
    downloadBlob(blob, `${product.value?.code || 'product'}.${ext}`)
    ElMessage.success('导出成功')
  } catch {
    // 拦截器已提示
  } finally {
    exporting.value = false
  }
}

async function onPreviewManual() {
  try {
    const html = await getManualHtml(productId)
    if (manualBlobUrl.value) URL.revokeObjectURL(manualBlobUrl.value)
    manualBlobUrl.value = URL.createObjectURL(new Blob([html], { type: 'text/html;charset=utf-8' }))
    manualVisible.value = true
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
  margin-bottom: 20px;
}
.head-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.status-steps {
  margin-bottom: 20px;
}
.action-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.error-alert {
  margin-bottom: 16px;
}
.desc {
  margin-top: 8px;
}
.compliance-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.compliance-item {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 16px;
}
.compliance-item.passed {
  border-left: 4px solid #67c23a;
  background: #f0f9eb;
}
.compliance-item.failed {
  border-left: 4px solid #f56c6c;
  background: #fef0f0;
}
.c-icon {
  font-size: 22px;
}
.passed .c-icon {
  color: #67c23a;
}
.failed .c-icon {
  color: #f56c6c;
}
.c-main {
  flex: 1;
}
.c-name {
  font-weight: 600;
  font-size: 14px;
}
.c-code {
  font-weight: 400;
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}
.c-detail {
  color: #606266;
  font-size: 12px;
  margin-top: 4px;
}
.manual-frame {
  width: 100%;
  height: 68vh;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}
</style>
