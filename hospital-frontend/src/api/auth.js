import request from './request'
import { useMock } from '../utils/mock'
import { mockStaffLogin } from '../mock/auth'

export function staffLogin(data) {
  if (useMock()) return mockStaffLogin(data)
  return request.post('/auth/staff/login', data)
}

export function fetchAuthHealth() {
  if (useMock()) {
    return Promise.resolve({ code: 200, success: true, data: { status: 'MOCK' } })
  }
  return request.get('/auth/health')
}
