import dayjs from 'dayjs'

export function formatTime(val?: string | null): string {
  if (!val) return '-'
  const d = dayjs(val)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : String(val)
}

export function formatBytes(bytes?: number | null): string {
  if (bytes === null || bytes === undefined) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

export function secretLevelText(level?: number): string {
  switch (level) {
    case 1: return '公开'
    case 2: return '内部'
    case 3: return '秘密'
    case 4: return '机密'
    default: return level ? `L${level}` : '-'
  }
}

export function secretLevelTagType(level?: number): 'success' | 'primary' | 'warning' | 'danger' | 'info' {
  switch (level) {
    case 1: return 'success'
    case 2: return 'primary'
    case 3: return 'warning'
    case 4: return 'danger'
    default: return 'info'
  }
}

export function modalityText(m?: string): string {
  const map: Record<string, string> = {
    STRUCTURED: '结构化',
    TEXT: '文本',
    IMAGE: '图像',
    VIDEO: '视频'
  }
  return (m && map[m]) || m || '-'
}

export function productFormText(form?: string): string {
  const map: Record<string, string> = {
    DATA_PACKAGE: '数据包',
    API_SERVICE: 'API服务',
    REPORT: '报告'
  }
  return (form && map[form]) || form || '-'
}

export function productStatusText(status?: string): string {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    CONFIGURED: '已配置',
    GENERATED: '已生成',
    CHECKING: '校验中',
    PASSED: '合规通过',
    BLOCKED: '合规拦阻',
    REGISTERED: '已登记',
    LISTED: '已挂牌',
    DELIVERED: '已交付'
  }
  return (status && map[status]) || status || '-'
}

export function productStatusTagType(status?: string): 'success' | 'primary' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'LISTED': return 'success'
    case 'PASSED':
    case 'REGISTERED': return 'primary'
    case 'BLOCKED': return 'danger'
    case 'GENERATED':
    case 'CONFIGURED': return 'warning'
    default: return 'info'
  }
}

export function fileStatusText(status?: string): string {
  const map: Record<string, string> = {
    PENDING: '待处理',
    RUNNING: '处理中',
    PROCESSING: '处理中',
    SUCCESS: '成功',
    READY: '就绪',
    FAILED: '失败',
    REPAIRING: '修复中'
  }
  return (status && map[status]) || status || '-'
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
