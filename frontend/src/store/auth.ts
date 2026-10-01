import { defineStore } from 'pinia'

export interface UserInfo {
  username: string
  realName: string
  roles: string[]
  secretLevel: number
  deptCode: string
  permissions: string[]
}

interface AuthState {
  accessToken: string
  refreshToken: string
  user: UserInfo | null
}

const STORAGE_KEY = 'mydatama_auth'

function loadState(): AuthState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      return {
        accessToken: parsed.accessToken || '',
        refreshToken: parsed.refreshToken || '',
        user: parsed.user || null
      }
    }
  } catch {
    // ignore
  }
  return { accessToken: '', refreshToken: '', user: null }
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => loadState(),
  getters: {
    isLoggedIn: (state) => !!state.accessToken,
    permissions: (state) => state.user?.permissions || []
  },
  actions: {
    setLogin(data: { accessToken: string; refreshToken: string; user: UserInfo }) {
      this.accessToken = data.accessToken
      this.refreshToken = data.refreshToken
      this.user = data.user
      this.persist()
    },
    setTokens(accessToken: string, refreshToken: string) {
      this.accessToken = accessToken
      this.refreshToken = refreshToken
      this.persist()
    },
    setUser(user: UserInfo) {
      this.user = user
      this.persist()
    },
    logout() {
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
      localStorage.removeItem(STORAGE_KEY)
    },
    persist() {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          accessToken: this.accessToken,
          refreshToken: this.refreshToken,
          user: this.user
        })
      )
    },
    hasPermission(code: string): boolean {
      if (!this.user) return false
      if (this.user.roles?.includes('admin')) return true
      return this.user.permissions?.includes(code)
    }
  }
})
