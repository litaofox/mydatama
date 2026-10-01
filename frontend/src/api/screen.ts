import http from './http'

export interface ScreenOverview {
  assetCount: number
  fileCount: number
  datasetCount: number
  productCount: number
  avgQuality: number
  todayUploads: number
}

export interface ScreenTrend {
  dates: string[]
  uploads: number[]
  assetsReady: number[]
}

export interface ScreenDistribution {
  modalityDist: { name: string; value: number }[]
  domainDist: { name: string; value: number }[]
}

export interface ScreenOps {
  list: { username: string; action: string; path: string; createdAt: string }[]
}

export interface ScreenProduct {
  totalProducts: number
  listedProducts: number
  blockedProducts: number
  compliancePassRate: number
  formDistribution: { form: string; count: number }[]
  latest: { code: string; name: string; status: string; listedAt: string }[]
}

export function getOverview(): Promise<ScreenOverview> {
  return http.get('/api/screen/overview')
}

export function getTrend(days = 7): Promise<ScreenTrend> {
  return http.get('/api/screen/trend', { params: { days } })
}

export function getDistribution(): Promise<ScreenDistribution> {
  return http.get('/api/screen/distribution')
}

export function getOps(): Promise<ScreenOps> {
  return http.get('/api/screen/ops')
}

export function getScreenProduct(): Promise<ScreenProduct> {
  return http.get('/api/screen/product')
}
