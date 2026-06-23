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
  doctor02: {
    userId: 7,
    employeeId: 7,
    realName: '李医生',
    roles: ['OUTPATIENT_DOCTOR'],
    deptId: 1,
    deptName: '内科',
  },
  doctor03: {
    userId: 8,
    employeeId: 8,
    realName: '陈教授',
    roles: ['OUTPATIENT_DOCTOR'],
    deptId: 1,
    deptName: '内科',
  },
  doctor04: {
    userId: 9,
    employeeId: 9,
    realName: '王医生',
    roles: ['OUTPATIENT_DOCTOR'],
    deptId: 6,
    deptName: '外科',
  },
  doctor05: {
    userId: 10,
    employeeId: 10,
    realName: '刘教授',
    roles: ['OUTPATIENT_DOCTOR'],
    deptId: 6,
    deptName: '外科',
  },
  doctor06: {
    userId: 12,
    employeeId: 12,
    realName: '赵医生',
    roles: ['OUTPATIENT_DOCTOR'],
    deptId: 6,
    deptName: '外科',
  },
  lab01: {
    userId: 2,
    employeeId: 2,
    realName: '李检验',
    roles: ['LAB_DOCTOR'],
    deptId: 3,
    deptName: '检验科',
  },
  lab02: {
    userId: 13,
    employeeId: 13,
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
  check02: {
    userId: 14,
    employeeId: 15,
    realName: '李影像',
    roles: ['CHECK_DOCTOR'],
    deptId: 2,
    deptName: '放射科',
  },
  check03: {
    userId: 15,
    employeeId: 16,
    realName: '陈影像',
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
    deptId: 8,
    deptName: '信息科',
  },
  disposal01: {
    userId: 11,
    employeeId: 11,
    realName: '孙处置',
    roles: ['DISPOSAL_DOCTOR'],
    deptId: 7,
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
