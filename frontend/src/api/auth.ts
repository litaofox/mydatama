import http from './http'
import type { UserInfo } from '@/store/auth'

export interface LoginResult {
  accessToken: string
  refreshToken: string
  user: UserInfo
}

export function login(data: { username: string; password: string }): Promise<LoginResult> {
  return http.post('/api/auth/login', data)
}

export function logout(): Promise<void> {
  return http.post('/api/auth/logout')
}

export function getMe(): Promise<UserInfo> {
  return http.get('/api/auth/me')
}
