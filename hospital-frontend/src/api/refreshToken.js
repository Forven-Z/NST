import axios from 'axios'
import { useMock } from '../utils/mock'
import { mockStaffRefresh } from '../mock/auth'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

/** 独立 axios 调用，不走 request 拦截器，避免 refresh 401 时死循环 */
export function refreshStaffToken(refreshToken) {
  if (useMock()) return mockStaffRefresh(refreshToken)
  return axios
    .post(`${baseURL}/auth/token/refresh`, { refreshToken }, {
      headers: { 'Content-Type': 'application/json' },
      timeout: 15000,
    })
    .then((res) => {
      const payload = res.data
      if (payload && typeof payload.success === 'boolean' && !payload.success) {
        return Promise.reject(new Error(payload.message || '刷新 Token 失败'))
      }
      return payload
    })
}
