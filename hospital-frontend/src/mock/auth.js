import { mockResult } from '../utils/mock'
import { getAuthAccounts } from './staff-registry'

const ACCOUNTS = {
  doctor01: {
    userId: 1,
    employeeId: 1,
    realName: '张医生',
    roles: ['OUTPATIENT_DOCTOR'],
    deptId: 1,
    deptName: '内科',
  },
  lab01: {
    userId: 2,
    employeeId: 2,
    realName: '李检验',
    roles: ['LAB_DOCTOR'],
    deptId: 3,
    deptName: '检验科',
  },
  inspection01: {
    userId: 8,
    employeeId: 8,
    realName: '周检验',
    roles: ['LAB_DOCTOR'],
    deptId: 3,
    deptName: '检验科',
  },
  check01: {
    userId: 3,
    employeeId: 3,
    realName: '王检查',
    roles: ['CHECK_DOCTOR'],
    deptId: 2,
    deptName: '放射科',
  },
  pharmacy01: {
    userId: 4,
    employeeId: 4,
    realName: '赵药师',
    roles: ['PHARMACIST'],
    deptId: 4,
    deptName: '药房',
  },
  registrar01: {
    userId: 5,
    employeeId: 5,
    realName: '钱收费',
    roles: ['REGISTRAR'],
    deptId: 5,
    deptName: '挂号处',
  },
  admin: {
    userId: 6,
    employeeId: 6,
    realName: '系统管理员',
    roles: ['ADMIN'],
    deptId: 5,
    deptName: '管理',
  },
  disposal01: {
    userId: 7,
    employeeId: 19,
    realName: '孙处置',
    roles: ['DISPOSAL_DOCTOR'],
    deptId: 6,
    deptName: '处置科',
  },
}

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

export function mockStaffRefresh(refreshToken) {
  if (!refreshToken || !String(refreshToken).startsWith('mock-refresh-')) {
    return Promise.reject(new Error('【Mock】无效的 refreshToken'))
  }
  const username = String(refreshToken).slice('mock-refresh-'.length)
  const accounts = getAuthAccounts()
  const profile = accounts[username] || ACCOUNTS[username]
  if (!profile) {
    return Promise.reject(new Error('【Mock】refreshToken 已失效，请重新登录'))
  }
  return mockResult({
    accessToken: `mock-token-${username}`,
    refreshToken: `mock-refresh-${username}`,
    expiresIn: 7200,
    tokenType: 'Bearer',
  })
}
