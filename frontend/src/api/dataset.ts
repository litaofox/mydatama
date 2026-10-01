import http from './http'

export interface Dataset {
  id: number
  name: string
  scenario: string
  description: string
  creator: string
  createdAt: string
}

export interface DatasetVersion {
  id: number
  versionNo: number
  itemCount: number
  qualityReport: Record<string, any>
  changeNote: string
  createdAt: string
}

export interface DatasetVersionDetail extends DatasetVersion {
  items: { assetId: number; assetSnapshot: Record<string, any> }[]
}

export interface VersionCompareResult {
  added: any[]
  removed: any[]
  metricsDiff: Record<string, any>
}

export function getDatasets(): Promise<Dataset[]> {
  return http.get('/api/dataset/datasets')
}

export function createDataset(data: {
  name: string
  scenario: string
  description: string
  filterCond: {
    modality?: string
    bizDomain?: string
    minQualityScore?: number
    secretLevelMax?: number
  }
}): Promise<any> {
  return http.post('/api/dataset/datasets', data)
}

export function createVersion(datasetId: number | string, changeNote: string): Promise<{ versionId: number; versionNo: number; itemCount: number }> {
  return http.post(`/api/dataset/datasets/${datasetId}/versions`, { changeNote })
}

export function getVersions(datasetId: number | string): Promise<DatasetVersion[]> {
  return http.get(`/api/dataset/datasets/${datasetId}/versions`)
}

export function getVersionDetail(versionId: number | string): Promise<DatasetVersionDetail> {
  return http.get(`/api/dataset/versions/${versionId}`)
}

export function compareVersions(a: number | string, b: number | string): Promise<VersionCompareResult> {
  return http.get(`/api/dataset/versions/${a}/compare/${b}`)
}
