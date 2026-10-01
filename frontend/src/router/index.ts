import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/screen',
    name: 'Screen',
    component: () => import('@/views/Screen.vue'),
    meta: { title: '可视化大屏' }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/screen',
    children: [
      { path: 'processing/upload', name: 'ProcUpload', component: () => import('@/views/processing/Upload.vue'), meta: { title: '文件上传' } },
      { path: 'processing/files', name: 'ProcFiles', component: () => import('@/views/processing/FileList.vue'), meta: { title: '文件列表' } },
      { path: 'processing/files/:id', name: 'ProcFileDetail', component: () => import('@/views/processing/FileDetail.vue'), meta: { title: '文件详情' } },
      { path: 'processing/annotation/:fileId', name: 'ProcAnnotation', component: () => import('@/views/processing/Annotation.vue'), meta: { title: '人工标注' } },
      { path: 'governance/assets', name: 'GovAssets', component: () => import('@/views/governance/AssetList.vue'), meta: { title: '资产目录' } },
      { path: 'governance/assets/:id', name: 'GovAssetDetail', component: () => import('@/views/governance/AssetDetail.vue'), meta: { title: '资产详情' } },
      { path: 'governance/lineage/:id', name: 'GovLineage', component: () => import('@/views/governance/Lineage.vue'), meta: { title: '血缘图' } },
      { path: 'governance/heatmap', name: 'GovHeatmap', component: () => import('@/views/governance/Heatmap.vue'), meta: { title: '数据热力图' } },
      { path: 'governance/standards', name: 'GovStandards', component: () => import('@/views/governance/Standards.vue'), meta: { title: '数据标准' } },
      { path: 'governance/quality-rules', name: 'GovQualityRules', component: () => import('@/views/governance/QualityRules.vue'), meta: { title: '质量规则' } },
      { path: 'dataset/list', name: 'DatasetList', component: () => import('@/views/dataset/DatasetList.vue'), meta: { title: '数据集' } },
      { path: 'dataset/:id', name: 'DatasetDetail', component: () => import('@/views/dataset/DatasetDetail.vue'), meta: { title: '数据集详情' } },
      { path: 'product/list', name: 'ProductList', component: () => import('@/views/product/ProductList.vue'), meta: { title: '数据产品' } },
      { path: 'product/create', name: 'ProductCreate', component: () => import('@/views/product/ProductCreate.vue'), meta: { title: '创建产品' } },
      { path: 'product/:id', name: 'ProductDetail', component: () => import('@/views/product/ProductDetail.vue'), meta: { title: '产品详情' } },
      { path: 'iam/users', name: 'IamUsers', component: () => import('@/views/iam/Users.vue'), meta: { title: '用户管理' } },
      { path: 'iam/roles', name: 'IamRoles', component: () => import('@/views/iam/Roles.vue'), meta: { title: '角色管理' } },
      { path: 'iam/policies', name: 'IamPolicies', component: () => import('@/views/iam/Policies.vue'), meta: { title: 'ABAC 策略' } },
      { path: 'iam/audit', name: 'IamAudit', component: () => import('@/views/iam/Audit.vue'), meta: { title: '审计日志' } },
      { path: 'openapi-demo', name: 'OpenApiDemo', component: () => import('@/views/openapi/OpenApiDemo.vue'), meta: { title: '开放API演示' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (to.path === '/login' && auth.isLoggedIn) {
      return { path: '/' }
    }
    return true
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
