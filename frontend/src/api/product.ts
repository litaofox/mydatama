import http from './http'
import type { PageResult } from './iam'

export interface ProductTemplate {
  id: number
  code: string
  name: string
  form: string
  description: string
  configSchema: any
  version: string
}

export interface Product {
  id: number
  code: string
  name: string
  form: string
  status: string
  category: string
  pricingModel: string
  price: number
  listedAt: string
  createdAt: string
}

export interface ComplianceItem {
  ruleCode: string
  ruleName: string
  passed: boolean
  detail: string
}

export interface ComplianceRecord extends ComplianceItem {
  batchNo: string
  runAt: string
}

export interface ProductArtifact {
  id: number
  artifactType: string
  filePath: string
  fileSize: number
  checksum: string
}

export interface ProductDetail extends Product {
  templateId: number
  datasetId: number
  datasetVersionId: number
  datasetVersionNo: number
  secretLevel: number
  description: string
  manualMeta: Record<string, any>
  configParams: Record<string, any>
  regNo: string
  lastError: string
  latestCompliance: {
    batchNo: string
    status: string
    items: ComplianceItem[]
  } | null
  artifacts: ProductArtifact[]
}

export function getTemplates(): Promise<ProductTemplate[]> {
  return http.get('/api/product/templates')
}

export function createProduct(data: any): Promise<{ id: number; status: string }> {
  return http.post('/api/product/products', data)
}

export function getProducts(params: {
  status?: string
  form?: string
  keyword?: string
  page: number
  size: number
}): Promise<PageResult<Product>> {
  return http.get('/api/product/products', { params })
}

export function getProductDetail(id: number | string): Promise<ProductDetail> {
  return http.get(`/api/product/products/${id}`)
}

export function updateProduct(id: number | string, data: any): Promise<any> {
  return http.put(`/api/product/products/${id}`, data)
}

export function configureProduct(id: number | string): Promise<any> {
  return http.post(`/api/product/products/${id}/configure`)
}

export function generateProduct(id: number | string): Promise<any> {
  return http.post(`/api/product/products/${id}/generate`)
}

export function runCompliance(id: number | string): Promise<{ batchNo: string; status: string; items: ComplianceItem[] }> {
  return http.post(`/api/product/products/${id}/compliance/run`)
}

export function getCompliance(id: number | string): Promise<ComplianceRecord[]> {
  return http.get(`/api/product/products/${id}/compliance`)
}

export function registerProduct(id: number | string): Promise<any> {
  return http.post(`/api/product/products/${id}/register`)
}

export function listingProduct(id: number | string): Promise<any> {
  return http.post(`/api/product/products/${id}/listing`)
}

export function exportProduct(id: number | string): Promise<Blob> {
  return http
    .get(`/api/product/products/${id}/export`, { responseType: 'blob', timeout: 300000 })
    .then((resp: any) => resp.data as Blob)
}

export function getManualHtml(id: number | string): Promise<string> {
  return http
    .get(`/api/product/products/${id}/manual`, { responseType: 'text' as any })
    .then((resp: any) => resp.data as string)
}
