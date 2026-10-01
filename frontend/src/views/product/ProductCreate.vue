<template>
  <div>
    <div class="page-card">
      <h3 class="card-title">创建数据产品</h3>
      <el-steps :active="step" align-center finish-status="success" class="steps">
        <el-step title="选择模板" />
        <el-step title="选择原料" />
        <el-step title="填写元数据" />
      </el-steps>

      <!-- 步骤1：模板选择 -->
      <div v-show="step === 0" class="step-body">
        <div class="tpl-cards">
          <div
            v-for="tpl in templates"
            :key="tpl.id"
            class="tpl-card"
            :class="{ active: form.templateId === tpl.id }"
            @click="selectTemplate(tpl)"
          >
            <div class="tpl-name">{{ tpl.name }}</div>
            <el-tag size="small" effect="plain">{{ productFormText(tpl.form) }}</el-tag>
            <div class="tpl-desc">{{ tpl.description }}</div>
          </div>
        </div>
      </div>

      <!-- 步骤2：数据集/版本 -->
      <div v-show="step === 1" class="step-body narrow">
        <el-form label-width="110px">
          <el-form-item label="数据集" required>
            <el-select v-model="form.datasetId" placeholder="选择数据集" style="width: 100%" @change="onDatasetChange">
              <el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="原料版本" required>
            <el-select v-model="form.datasetVersionId" placeholder="选择版本" style="width: 100%" :disabled="!versions.length">
              <el-option
                v-for="v in versions"
                :key="v.id"
                :label="`v${v.versionNo}（${v.itemCount} 个资产，质量 ${Number(v.qualityReport?.overall_score ?? 0).toFixed(2)}）`"
                :value="v.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- 步骤3：元数据 -->
      <div v-show="step === 2" class="step-body narrow">
        <el-form label-width="110px">
          <el-form-item label="产品名称" required>
            <el-input v-model="form.name" />
          </el-form-item>
          <el-form-item label="产品编码" required>
            <el-input v-model="form.code" placeholder="如 PRD-20261001-001" />
          </el-form-item>
          <el-form-item label="分类">
            <el-input v-model="form.category" placeholder="如 交通物流" />
          </el-form-item>
          <el-form-item label="密级">
            <el-select v-model="form.secretLevel" style="width: 100%">
              <el-option :value="1" label="1 公开" />
              <el-option :value="2" label="2 内部" />
              <el-option :value="3" label="3 秘密" />
              <el-option :value="4" label="4 机密" />
            </el-select>
          </el-form-item>
          <el-form-item label="定价方式">
            <el-select v-model="form.pricingModel" style="width: 100%">
              <el-option value="PER_CALL" label="按次" />
              <el-option value="PER_MONTH" label="按月" />
              <el-option value="ONE_TIME" label="买断" />
            </el-select>
          </el-form-item>
          <el-form-item label="价格（元）">
            <el-input-number v-model="form.price" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="2" />
          </el-form-item>
          <el-divider content-position="left">说明书四要素</el-divider>
          <el-form-item label="来源说明" required>
            <el-input v-model="form.manualMeta.sourceDesc" />
          </el-form-item>
          <el-form-item label="字段说明" required>
            <el-input v-model="form.manualMeta.fieldDesc" />
          </el-form-item>
          <el-form-item label="更新频率" required>
            <el-select v-model="form.manualMeta.updateFreq" style="width: 100%">
              <el-option value="DAILY" label="每日" />
              <el-option value="WEEKLY" label="每周" />
              <el-option value="MONTHLY" label="每月" />
              <el-option value="ONE_TIME" label="一次性" />
            </el-select>
          </el-form-item>
          <el-form-item label="交付方式" required>
            <el-input v-model="form.manualMeta.deliveryMode" placeholder="如 数据包下载 / API 调用" />
          </el-form-item>
          <template v-if="configFields.length">
            <el-divider content-position="left">模板参数（configParams）</el-divider>
            <el-form-item v-for="field in configFields" :key="field.key" :label="field.label">
              <el-switch v-if="field.type === 'boolean'" v-model="configParams[field.key]" />
              <el-input-number v-else-if="field.type === 'number' || field.type === 'integer'" v-model="configParams[field.key]" style="width: 100%" />
              <el-select v-else-if="field.options?.length" v-model="configParams[field.key]" style="width: 100%">
                <el-option v-for="opt in field.options" :key="opt" :label="opt" :value="opt" />
              </el-select>
              <el-input v-else v-model="configParams[field.key]" />
            </el-form-item>
          </template>
        </el-form>
      </div>

      <div class="step-actions">
        <el-button v-if="step > 0" @click="step--">上一步</el-button>
        <el-button v-if="step < 2" type="primary" :disabled="!canNext" @click="step++">下一步</el-button>
        <el-button v-else type="primary" :loading="submitting" @click="onSubmit">创建产品</el-button>
        <el-button @click="$router.push('/product/list')">取消</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { getTemplates, createProduct, type ProductTemplate } from '@/api/product'
import { getDatasets, getVersions, type Dataset, type DatasetVersion } from '@/api/dataset'
import { productFormText } from '@/utils/format'

const router = useRouter()

const step = ref(0)
const templates = ref<ProductTemplate[]>([])
const datasets = ref<Dataset[]>([])
const versions = ref<DatasetVersion[]>([])
const submitting = ref(false)

const form = reactive({
  templateId: 0,
  datasetId: undefined as number | undefined,
  datasetVersionId: undefined as number | undefined,
  name: '',
  code: `PRD-${dayjs().format('YYYYMMDD')}-001`,
  category: '',
  secretLevel: 2,
  pricingModel: 'PER_MONTH',
  price: 1999,
  description: '',
  manualMeta: {
    sourceDesc: '',
    fieldDesc: '',
    updateFreq: 'MONTHLY',
    deliveryMode: ''
  }
})

const configParams = reactive<Record<string, any>>({})

interface ConfigField {
  key: string
  label: string
  type: string
  default?: any
  options?: string[]
}

const selectedTemplate = computed(() => templates.value.find((t) => t.id === form.templateId))

const configFields = computed<ConfigField[]>(() => {
  const schema = selectedTemplate.value?.configSchema
  if (!schema) return []
  const obj = typeof schema === 'string' ? safeParse(schema) : schema
  if (!obj) return []
  // 支持 { properties: { key: {type,title,default,enum} } } 或 { key: {...} } 两种形态
  const props = obj.properties || obj
  return Object.entries(props)
    .filter(([, v]) => v && typeof v === 'object')
    .map(([key, v]: [string, any]) => ({
      key,
      label: v.title || v.label || key,
      type: v.type || 'string',
      default: v.default,
      options: v.enum
    }))
})

function safeParse(s: string) {
  try {
    return JSON.parse(s)
  } catch {
    return null
  }
}

watch(configFields, (fields) => {
  for (const f of fields) {
    if (!(f.key in configParams)) {
      configParams[f.key] = f.default ?? (f.type === 'boolean' ? false : f.type === 'number' || f.type === 'integer' ? 0 : '')
    }
  }
}, { immediate: true })

const canNext = computed(() => {
  if (step.value === 0) return !!form.templateId
  if (step.value === 1) return !!form.datasetId && !!form.datasetVersionId
  return true
})

function selectTemplate(tpl: ProductTemplate) {
  form.templateId = tpl.id
}

async function onDatasetChange(id: number) {
  form.datasetVersionId = undefined
  versions.value = []
  try {
    versions.value = await getVersions(id)
  } catch {
    // 拦截器已提示
  }
}

async function onSubmit() {
  if (!form.name.trim() || !form.code.trim()) {
    ElMessage.warning('产品名称与编码必填')
    return
  }
  if (!form.manualMeta.sourceDesc || !form.manualMeta.fieldDesc || !form.manualMeta.updateFreq || !form.manualMeta.deliveryMode) {
    ElMessage.warning('说明书四要素必填（来源/字段/更新频率/交付方式）')
    return
  }
  submitting.value = true
  try {
    const result = await createProduct({
      templateId: form.templateId,
      name: form.name,
      code: form.code,
      datasetId: form.datasetId,
      datasetVersionId: form.datasetVersionId,
      secretLevel: form.secretLevel,
      category: form.category,
      pricingModel: form.pricingModel,
      price: form.price,
      description: form.description,
      manualMeta: { ...form.manualMeta },
      configParams: { ...configParams }
    })
    ElMessage.success('产品创建成功（草稿）')
    router.push(`/product/${result.id}`)
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    const [tpls, dss] = await Promise.all([getTemplates(), getDatasets()])
    templates.value = tpls
    datasets.value = dss
  } catch {
    // 拦截器已提示
  }
})
</script>

<style scoped>
.card-title {
  margin: 0 0 20px;
  font-size: 15px;
}
.steps {
  margin-bottom: 28px;
}
.step-body {
  min-height: 280px;
}
.step-body.narrow {
  max-width: 640px;
  margin: 0 auto;
}
.tpl-cards {
  display: flex;
  gap: 16px;
  justify-content: center;
}
.tpl-card {
  width: 240px;
  border: 2px solid #e4e7ed;
  border-radius: 10px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s;
}
.tpl-card:hover {
  border-color: #a0cfff;
}
.tpl-card.active {
  border-color: #409eff;
  background: #ecf5ff;
}
.tpl-name {
  font-weight: 600;
  margin-bottom: 8px;
}
.tpl-desc {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.step-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 24px;
}
</style>
