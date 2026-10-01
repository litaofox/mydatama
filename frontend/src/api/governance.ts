import http from './http'
import type { PageResult } from './iam'

export interface Asset {
  id: number
  name: string
  assetType: string
  modality: string
  bizDomain: string
  secretLevel: number
  qualityScore: number
  status: string
  storageRef: string
  createdAt: string
}

export interface AssetColumn {
  colName: string
  dataType: string
  sensitive: boolean
  maskStrategy: string
}

export interface AssetDetail extends Asset {
  ownerDept: string
  columns: AssetColumn[]
  tags: any[]
  usage: any[]
  ext: Record<string, any>
}

export interface LineageNode {
  id: number
  name: string
  assetType: string
  modality: string
  level: number
}

export interface LineageEdge {
  from: number
  to: number
  relType: string
}

export interface HeatmapData {
  domains: string[]
  dates: string[]
  data: [number, number, number][]
}

export interface Standard {
  id: number
  code: string
  name: string
  ruleExpr: string
  description: string
  enabled: boolean
}

export interface QualityRule {
  id: number
  targetRef: string
  checkType: string
  expr: string
  standardId: number
  enabled: boolean
}

export function getAssets(params: {
  keyword?: string
  modality?: string
  assetType?: string
  bizDomain?: string
  page: number
  size: number
}): Promise<PageResult<Asset>> {
  return http.get('/api/governance/assets', { params })
}

export function getAssetDetail(id: number | string): Promise<AssetDetail> {
  return http.get(`/api/governance/assets/${id}`)
}

export function getLineage(id: number | string, depth = 3): Promise<{ nodes: LineageNode[]; edges: LineageEdge[] }> {
  return http.get(`/api/governance/assets/lineage/${id}`, { params: { depth } })
}

export function getHeatmap(days = 30): Promise<HeatmapData> {
  return http.get('/api/governance/assets/heatmap', { params: { days } })
}

export function getStandards(): Promise<Standard[]> {
  return http.get('/api/governance/standards')
}

export function createStandard(data: { code: string; name: string; ruleExpr: string; description: string }): Promise<any> {
  return http.post('/api/governance/standards', data)
}

export function getQualityRules(targetRef: string): Promise<QualityRule[]> {
  return http.get('/api/governance/quality-rules', { params: { targetRef } })
}
