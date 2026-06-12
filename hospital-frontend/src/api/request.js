import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import router from '../router'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

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
            const auth = useAuthStore()
            auth.logout()
            router.push({ name: 'login' })
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
        const auth = useAuthStore()
        auth.logout()
        router.push({ name: 'login' })
      }
      return Promise.reject(new Error(payload.message || '请求失败'))
    }
    return payload
  },
  (error) => {
    let message = error.response?.data?.message || error.message || '网络异常'
    if (!error.response) {
      message =
        '无法连接 Gateway（127.0.0.1:9000）。请先启动 Nacos + hospital-auth + hospital-gateway，或使用 npm run dev（非 preview）。仅看界面可在 .env.development 设 VITE_USE_MOCK=true'
    }
    if (error.response?.status === 401) {
      const auth = useAuthStore()
      auth.logout()
      router.push({ name: 'login' })
    }
    return Promise.reject(new Error(message))
  },
)

export default request
