import { mockResult } from '../utils/mock'

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
  if (password !== '123456') {
    return Promise.reject(new Error('【Mock】密码错误，开发账号密码均为 123456'))
  }
  const profile = ACCOUNTS[username]
  if (!profile) {
    return Promise.reject(new Error('【Mock】未知账号'))
  }
  return mockResult({
    accessToken: `mock-token-${username}`,
    refreshToken: `mock-refresh-${username}`,
    expiresIn: 7200,
    ...profile,
  })
}
