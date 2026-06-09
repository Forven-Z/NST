import { mockResult } from '../utils/mock'
import { getAuthAccounts } from './staff-registry'

export function mockStaffLogin({ username, password }) {
  const accounts = getAuthAccounts()
  const profile = accounts[username]
  if (!profile) {
    return Promise.reject(new Error('【Mock】未知账号，请确认员工档案已开通登录'))
  }
  const expected = profile.password || '123456'
  if (password !== expected) {
    return Promise.reject(new Error(`【Mock】密码错误（该账号期望密码：${expected === '123456' ? '默认 123456' : '见建档时设置'}）`))
  }
  const { password: _pw, username: _un, ...rest } = profile
  return mockResult({
    accessToken: `mock-token-${username}`,
    refreshToken: `mock-refresh-${username}`,
    expiresIn: 7200,
    ...rest,
  })
}
