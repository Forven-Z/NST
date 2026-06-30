import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import router from '../router'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

let isRefreshing = false
/** @type {Array<(token: string | null) => void>} */
let pendingRequests = []

function isAuthRefreshSkipped(config) {
  if (!config) return true
  if (config._skipAuthRefresh || config._retry) return true
  const url = config.url || ''
  return url.includes('/auth/staff/login') || url.includes('/auth/token/refresh')
}

function redirectToLogin() {
  const auth = useAuthStore()
  auth.logout()
  if (router.currentRoute.value.name !== 'login') {
    router.push({ name: 'login' })
  }
}

function flushPendingRequests(newToken) {
  pendingRequests.forEach((cb) => cb(newToken))
  pendingRequests = []
}

async function handleUnauthorized(config) {
  if (isAuthRefreshSkipped(config)) {
    return Promise.reject(new Error('未授权'))
  }

  const auth = useAuthStore()
  if (!auth.refreshToken) {
    redirectToLogin()
    return Promise.reject(new Error('登录已过期，请重新登录'))
  }

  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      pendingRequests.push((newToken) => {
        if (!newToken) {
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        config.headers.Authorization = `Bearer ${newToken}`
        config._retry = true
        resolve(request(config))
      })
    })
  }

  isRefreshing = true
  try {
    const newToken = await auth.refreshAccessToken()
    flushPendingRequests(newToken)
    config.headers.Authorization = `Bearer ${newToken}`
    config._retry = true
    return request(config)
  } catch {
    flushPendingRequests(null)
    redirectToLogin()
    return Promise.reject(new Error('登录已过期，请重新登录'))
  } finally {
    isRefreshing = false
  }
}

request.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

request.interceptors.response.use(
  async (response) => {
    if (response.config.responseType === 'blob') {
      const blob = response.data
      const contentType = response.headers['content-type'] || blob?.type || ''
      if (contentType.includes('application/json') || blob?.type === 'application/json') {
        const text = await blob.text()
        let message = '请求失败'
        try {
          const json = JSON.parse(text)
          message = json.message || message
          if (json.code === 401) {
            if (isAuthRefreshSkipped(response.config)) {
              return Promise.reject(new Error(message))
            }
            try {
              return await handleUnauthorized(response.config)
            } catch (err) {
              return Promise.reject(new Error(err.message || message))
            }
          }
        } catch {
          // ignore
        }
        return Promise.reject(new Error(message))
      }
      return blob
    }
    const payload = response.data
    if (payload && typeof payload.success === 'boolean' && !payload.success) {
      if (payload.code === 401) {
        if (isAuthRefreshSkipped(response.config)) {
          return Promise.reject(new Error(payload.message || '请求失败'))
        }
        try {
          return await handleUnauthorized(response.config)
        } catch (err) {
          return Promise.reject(new Error(err.message || payload.message || '请求失败'))
        }
      }
      return Promise.reject(new Error(payload.message || '请求失败'))
    }
    return payload
  },
  async (error) => {
    let message = error.response?.data?.message || error.message || '网络异常'
    if (!error.response) {
      message =
        '无法连接 Gateway（127.0.0.1:9000）。请先启动 Nacos + hospital-auth + hospital-gateway，或使用 npm run dev（非 preview）。仅看界面可在 .env.development 设 VITE_USE_MOCK=true'
    }
    if (error.response?.status === 401 && error.config) {
      if (isAuthRefreshSkipped(error.config)) {
        return Promise.reject(new Error(message))
      }
      try {
        return await handleUnauthorized(error.config)
      } catch (err) {
        return Promise.reject(new Error(err.message || message))
      }
    }
    return Promise.reject(new Error(message))
  },
)

export default request
