import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import router from '@/router'

const http: AxiosInstance = axios.create({
  baseURL: '/',
  timeout: 120000
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

let refreshingPromise: Promise<boolean> | null = null

async function tryRefresh(): Promise<boolean> {
  const auth = useAuthStore()
  if (!auth.refreshToken) return false
  if (!refreshingPromise) {
    refreshingPromise = axios
      .post('/api/auth/refresh', { refreshToken: auth.refreshToken })
      .then((resp) => {
        const body = resp.data
        if (body && body.code === 0 && body.data) {
          auth.setTokens(body.data.accessToken, body.data.refreshToken)
          return true
        }
        return false
      })
      .catch(() => false)
      .finally(() => {
        setTimeout(() => {
          refreshingPromise = null
        }, 0)
      })
  }
  return refreshingPromise
}

function goLogin() {
  const auth = useAuthStore()
  auth.logout()
  if (router.currentRoute.value.path !== '/login') {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

http.interceptors.response.use(
  async (response) => {
    const config = response.config
    // 二进制下载直接透传
    if (config.responseType === 'blob') {
      return response
    }
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      // token 失效类错误码
      if (body.code === 401 || body.code === 401001 || body.code === 401002) {
        const ok = await tryRefresh()
        if (ok) {
          return http(rebuildConfig(config))
        }
        ElMessage.error('登录已过期，请重新登录')
        goLogin()
        return Promise.reject(new Error(body.message || '未登录'))
      }
      ElMessage.error(body.message || `请求失败（${body.code}）`)
      return Promise.reject(Object.assign(new Error(body.message || '请求失败'), { code: body.code, body }))
    }
    return body
  },
  async (error) => {
    const status = error.response?.status
    if (status === 401) {
      const ok = await tryRefresh()
      if (ok && error.config) {
        return http(rebuildConfig(error.config))
      }
      ElMessage.error('登录已过期，请重新登录')
      goLogin()
      return Promise.reject(error)
    }
    if (status === 403) {
      ElMessage.error('无权限执行该操作')
    } else {
      const msg = error.response?.data?.message || error.message || '网络异常'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

function rebuildConfig(config: AxiosRequestConfig): AxiosRequestConfig {
  return { ...config, headers: { ...(config.headers || {}) } }
}

export default http
