import http from './http'
import type { PageResult } from './iam'

export interface ProcFile {
  id: number
  name: string
  modality: string
  bizDomain?: string
  sizeBytes: number
  status: string
  createdAt: string
}

export interface ProcFileDetail extends ProcFile {
  storagePath: string
  processedPath: string
  maskColumns: string[]
  qualityMetrics: Record<string, any>
}

export interface ProcJob {
  jobId: number
  stage: string
  status: string
  retryCount: number
  metrics: Record<string, any> | null
  maskColumns: string[] | null
  error: string | null
  updatedAt: string
}

export function uploadFile(file: File): Promise<ProcFile> {
  const form = new FormData()
  form.append('file', file)
  return http.post('/api/processing/files', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  })
}

export function getFiles(params: {
  page: number
  size: number
  keyword?: string
  modality?: string
}): Promise<PageResult<ProcFile>> {
  return http.get('/api/processing/files', { params })
}

export function getFileDetail(id: number | string): Promise<ProcFileDetail> {
  return http.get(`/api/processing/files/${id}`)
}

export function repairFile(id: number | string, fieldMapping: Record<string, string>): Promise<any> {
  return http.post(`/api/processing/files/${id}/repair`, { fieldMapping })
}

export function getFileJob(id: number | string): Promise<ProcJob> {
  return http.get(`/api/processing/files/${id}/job`)
}

export function retryJob(jobId: number | string): Promise<any> {
  return http.post(`/api/processing/jobs/${jobId}/retry`)
}

export function downloadFile(id: number | string, variant: 'raw' | 'processed'): Promise<Blob> {
  return http
    .get(`/api/processing/files/${id}/download`, { params: { variant }, responseType: 'blob' })
    .then((resp: any) => resp.data as Blob)
}

export function getThumbUrl(id: number | string): string {
  return `/api/processing/files/${id}/thumb`
}
