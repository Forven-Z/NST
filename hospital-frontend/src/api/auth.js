import request from './request'

export function staffLogin(data) {
  return request.post('/auth/staff/login', data)
}
