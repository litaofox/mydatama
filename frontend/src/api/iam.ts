import http from './http'

export interface PageResult<T> {
  total: number
  list: T[]
}

export interface IamUser {
  id: number
  username: string
  realName: string
  deptCode: string
  secretLevel: number
  enabled: boolean
  lastLoginAt: string
}

export interface IamRole {
  id: number
  code: string
  name: string
  description: string
  builtin: boolean | number
}

export interface IamPermission {
  id: number
  code: string
  name: string
  module: string
  resource: string
  action: string
}

export interface IamPolicy {
  id: number
  code: string
  name: string
  resource: string
  action: string
  effect: string
  conditionTree: string
  priority: number
  enabled: boolean
}

export interface AuditLog {
  id: number
  username: string
  action: string
  resource: string
  method: string
  path: string
  statusCode: number
  ip: string
  costMs: number
  createdAt: string
}

export function getUsers(params: { page: number; size: number; keyword?: string }): Promise<PageResult<IamUser>> {
  return http.get('/api/iam/users', { params })
}

export function createUser(data: {
  username: string
  password: string
  realName: string
  deptCode: string
  secretLevel: number
}): Promise<any> {
  return http.post('/api/iam/users', data)
}

export function updateUser(
  id: number,
  data: { realName: string; deptCode: string; secretLevel: number; enabled: boolean; roleIds: number[] }
): Promise<any> {
  return http.put(`/api/iam/users/${id}`, data)
}

export function getRoles(): Promise<IamRole[]> {
  return http.get('/api/iam/roles')
}

export function getRolePermissions(id: number): Promise<number[]> {
  return http.get(`/api/iam/roles/${id}/permissions`)
}

export function updateRolePermissions(id: number, permissionIds: number[]): Promise<any> {
  return http.put(`/api/iam/roles/${id}/permissions`, { permissionIds })
}

export function getPermissions(): Promise<IamPermission[]> {
  return http.get('/api/iam/permissions')
}

export function getPolicies(): Promise<IamPolicy[]> {
  return http.get('/api/iam/policies')
}

export function createPolicy(data: {
  code: string
  name: string
  resource: string
  action: string
  effect: string
  conditionTree: string
  priority: number
}): Promise<any> {
  return http.post('/api/iam/policies', data)
}

export function togglePolicy(id: number, enabled: boolean): Promise<any> {
  return http.put(`/api/iam/policies/${id}/enabled`, { enabled })
}

export function getAuditLogs(params: {
  username?: string
  action?: string
  from?: string
  to?: string
  page: number
  size: number
}): Promise<PageResult<AuditLog>> {
  return http.get('/api/iam/audit-logs', { params })
}

export function exportAuditLogs(): Promise<Blob> {
  return http
    .get('/api/iam/audit-logs/export', { responseType: 'blob' })
    .then((resp: any) => resp.data as Blob)
}
