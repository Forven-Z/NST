/**
 * 员工 / 科室主数据（Mock 可变存储）
 * 建档即开通登录：username + password（默认 123456）
 */
import { MOCK_DOCTORS, MOCK_STAFF_MEMBERS, MOCK_ALL_DEPARTMENTS } from './dict'

export const ROLE_TYPE_OPTIONS = [
  { value: 'OUTPATIENT_DOCTOR', label: '门诊医生' },
  { value: 'CHECK_DOCTOR', label: '检查医生' },
  { value: 'LAB_DOCTOR', label: '检验医生' },
  { value: 'DISPOSAL_DOCTOR', label: '处置医生' },
  { value: 'PHARMACIST', label: '药师' },
  { value: 'REGISTRAR', label: '挂号收费员' },
  { value: 'ADMIN', label: '管理员' },
]

const ROLE_TO_AUTH = {
  OUTPATIENT_DOCTOR: ['OUTPATIENT_DOCTOR'],
  CHECK_DOCTOR: ['CHECK_DOCTOR'],
  LAB_DOCTOR: ['LAB_DOCTOR'],
  DISPOSAL_DOCTOR: ['DISPOSAL_DOCTOR'],
  PHARMACIST: ['PHARMACIST'],
  REGISTRAR: ['REGISTRAR'],
  ADMIN: ['ADMIN'],
}

/** 种子账号与 employeeId 对齐 auth.js / seed-dict.sql */
const SEED_USERNAMES = {
  1: 'doctor01',
  2: 'lab01',
  3: 'check01',
  4: 'pharmacy01',
  5: 'registrar01',
  6: 'admin',
  7: 'doctor02',
  8: 'doctor03',
  9: 'doctor04',
  10: 'doctor05',
  11: 'disposal01',
  12: 'doctor06',
  13: 'lab02',
  15: 'check02',
  16: 'check03',
}

const SEED_USER_IDS = {
  1: 1, 2: 2, 3: 3, 4: 4, 5: 5, 6: 6,
  7: 7, 8: 8, 9: 9, 10: 10, 11: 11, 12: 12,
  13: 13, 15: 14, 16: 15,
}

const DEFAULT_PASSWORD = '123456'

let nextEmployeeId = 100
let nextDeptId = 100
let nextUserId = 100

const departments = MOCK_ALL_DEPARTMENTS.map((d) => ({ ...d, delmark: 0 }))
const authAccounts = {}

function deptNameOf(deptId) {
  return departments.find((d) => d.id === deptId && !d.delmark)?.deptName || ''
}

function slugUsername(empNo, realName) {
  const base = empNo.replace(/\W/g, '').toLowerCase() || 'user'
  return base.length >= 3 ? base : `emp${base}`
}

function registerAuth(emp, username, password = DEFAULT_PASSWORD, userId = null) {
  const uname = username.trim()
  if (!uname) throw new Error('登录用户名不能为空')
  for (const [key, acc] of Object.entries(authAccounts)) {
    if (key !== emp.username && acc.username === uname) {
      throw new Error(`用户名 ${uname} 已被占用`)
    }
  }
  if (emp.username && authAccounts[emp.username]) {
    delete authAccounts[emp.username]
  }
  const uid = userId ?? ++nextUserId
  emp.username = uname
  emp.userId = uid
  emp.password = password
  emp.accountStatus = emp.delmark ? 0 : 1
  authAccounts[uname] = {
    userId: uid,
    employeeId: emp.employeeId,
    realName: emp.realName,
    roles: ROLE_TO_AUTH[emp.roleType] || ['OUTPATIENT_DOCTOR'],
    deptId: emp.deptId,
    deptName: deptNameOf(emp.deptId),
    password,
    username: uname,
  }
}

function buildSeedEmployees() {
  const list = []
  for (const d of MOCK_DOCTORS) {
    list.push({
      employeeId: d.employeeId,
      empNo: d.empNo,
      realName: d.realName,
      gender: 1,
      deptId: d.deptId,
      title: d.title,
      roleType: 'OUTPATIENT_DOCTOR',
      phone: '',
      delmark: 0,
    })
  }
  for (const s of MOCK_STAFF_MEMBERS) {
    if (list.some((e) => e.employeeId === s.employeeId)) continue
    list.push({
      employeeId: s.employeeId,
      empNo: s.empNo,
      realName: s.realName,
      gender: 1,
      deptId: s.deptId,
      title: s.title,
      roleType: s.roleType,
      phone: '',
      delmark: 0,
    })
  }
  if (!list.some((e) => e.employeeId === 6)) {
    list.push({
      employeeId: 6,
      empNo: 'E006',
      realName: '系统管理员',
      gender: 1,
      deptId: 8,
      title: '系统管理员',
      roleType: 'ADMIN',
      phone: '',
      delmark: 0,
    })
  }
  for (const emp of list) {
    const username = SEED_USERNAMES[emp.employeeId] || slugUsername(emp.empNo, emp.realName)
    registerAuth(emp, username, DEFAULT_PASSWORD, SEED_USER_IDS[emp.employeeId] ?? null)
  }
  nextEmployeeId = Math.max(...list.map((e) => e.employeeId), nextEmployeeId) + 1
  return list
}

const employees = buildSeedEmployees()

export function getAuthAccounts() {
  return authAccounts
}

export function listDepartments(params = {}) {
  let list = departments.filter((d) => !d.delmark)
  if (params.deptType !== undefined && params.deptType !== null && params.deptType !== '') {
    list = list.filter((d) => d.deptType === Number(params.deptType))
  }
  if (params.keyword) {
    const kw = String(params.keyword).trim().toLowerCase()
    list = list.filter(
      (d) => d.deptName.toLowerCase().includes(kw) || d.deptCode.toLowerCase().includes(kw),
    )
  }
  return list.sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
}

export function getDepartmentById(id) {
  return departments.find((d) => d.id === Number(id))
}

export function createDepartment(data) {
  const code = String(data.deptCode || '').trim()
  const name = String(data.deptName || '').trim()
  if (!code || !name) throw new Error('科室编码与名称不能为空')
  if (departments.some((d) => d.deptCode === code && !d.delmark)) {
    throw new Error('科室编码已存在')
  }
  const row = {
    id: ++nextDeptId,
    deptCode: code,
    deptName: name,
    deptType: Number(data.deptType ?? 1),
    sortNo: Number(data.sortNo ?? departments.length + 1),
    delmark: 0,
  }
  departments.push(row)
  return row
}

export function updateDepartment(id, data) {
  const row = departments.find((d) => d.id === Number(id))
  if (!row || row.delmark) throw new Error('科室不存在')
  if (data.deptName) row.deptName = String(data.deptName).trim()
  if (data.deptType !== undefined) row.deptType = Number(data.deptType)
  if (data.sortNo !== undefined) row.sortNo = Number(data.sortNo)
  for (const emp of employees) {
    if (emp.deptId === row.id) {
      const acc = authAccounts[emp.username]
      if (acc) acc.deptName = row.deptName
    }
  }
  return row
}

export function deleteDepartment(id) {
  const row = departments.find((d) => d.id === Number(id))
  if (!row || row.delmark) throw new Error('科室不存在')
  const activeStaff = employees.filter((e) => e.deptId === row.id && !e.delmark)
  if (activeStaff.length) {
    throw new Error(`科室下仍有 ${activeStaff.length} 名在职员工，请先调整或停用`)
  }
  row.delmark = 1
  return row
}

export function enrichEmployee(row) {
  return {
    ...row,
    deptName: deptNameOf(row.deptId),
    roleTypeLabel: ROLE_TYPE_OPTIONS.find((r) => r.value === row.roleType)?.label || row.roleType,
    hasAccount: !!row.username,
    accountStatus: row.delmark ? 0 : (row.accountStatus ?? 1),
  }
}

export function listEmployees(params = {}) {
  let list = employees.filter((e) => {
    if (params.delmark !== undefined && params.delmark !== null && params.delmark !== '') {
      return e.delmark === Number(params.delmark)
    }
    return !e.delmark
  })
  if (params.deptId) list = list.filter((e) => e.deptId === Number(params.deptId))
  if (params.roleType) list = list.filter((e) => e.roleType === params.roleType)
  if (params.scheduleKind === 1 || params.scheduleKind === '1') {
    list = list.filter((e) => e.roleType === 'OUTPATIENT_DOCTOR')
  }
  if (params.scheduleKind === 2 || params.scheduleKind === '2') {
    list = list.filter((e) => e.roleType !== 'OUTPATIENT_DOCTOR' && e.roleType !== 'ADMIN')
  }
  if (params.keyword) {
    const kw = String(params.keyword).trim().toLowerCase()
    list = list.filter(
      (e) =>
        e.realName.toLowerCase().includes(kw) ||
        e.empNo.toLowerCase().includes(kw) ||
        (e.username && e.username.toLowerCase().includes(kw)),
    )
  }
  return list.map(enrichEmployee)
}

export function getEmployeeById(id) {
  const row = employees.find((e) => e.employeeId === Number(id))
  return row ? enrichEmployee(row) : null
}

export function getSubstitutePoolByDept(deptId) {
  return listEmployees({ deptId, delmark: 0 }).map((e) => ({
    employeeId: e.employeeId,
    empNo: e.empNo,
    realName: e.realName,
    title: e.title,
    deptId: e.deptId,
    roleType: e.roleType,
  }))
}

export function createEmployee(data) {
  const empNo = String(data.empNo || '').trim()
  const realName = String(data.realName || '').trim()
  if (!empNo || !realName) throw new Error('工号与姓名不能为空')
  if (!data.deptId) throw new Error('请选择科室')
  if (!data.roleType) throw new Error('请选择岗位角色')
  if (employees.some((e) => e.empNo === empNo && !e.delmark)) {
    throw new Error('工号已存在')
  }
  const username = String(data.username || '').trim() || slugUsername(empNo, realName)
  const row = {
    employeeId: ++nextEmployeeId,
    empNo,
    realName,
    gender: data.gender ?? null,
    deptId: Number(data.deptId),
    title: data.title || '',
    roleType: data.roleType,
    phone: data.phone || '',
    delmark: 0,
  }
  employees.push(row)
  registerAuth(row, username, data.password || DEFAULT_PASSWORD)
  return enrichEmployee(row)
}

export function updateEmployee(id, data) {
  const row = employees.find((e) => e.employeeId === Number(id))
  if (!row || row.delmark) throw new Error('员工不存在')
  if (data.empNo && data.empNo !== row.empNo) {
    if (employees.some((e) => e.empNo === data.empNo && !e.delmark && e.employeeId !== row.employeeId)) {
      throw new Error('工号已存在')
    }
    row.empNo = String(data.empNo).trim()
  }
  if (data.realName) row.realName = String(data.realName).trim()
  if (data.gender !== undefined) row.gender = data.gender
  if (data.deptId !== undefined) row.deptId = Number(data.deptId)
  if (data.title !== undefined) row.title = data.title
  if (data.roleType) row.roleType = data.roleType
  if (data.phone !== undefined) row.phone = data.phone
  if (data.username) {
    registerAuth(row, data.username, data.password || row.password || DEFAULT_PASSWORD, row.userId)
  } else if (data.password) {
    registerAuth(row, row.username, data.password, row.userId)
  } else if (row.username) {
    const acc = authAccounts[row.username]
    if (acc) {
      acc.realName = row.realName
      acc.deptId = row.deptId
      acc.deptName = deptNameOf(row.deptId)
      acc.roles = ROLE_TO_AUTH[row.roleType] || acc.roles
    }
  }
  return enrichEmployee(row)
}

export function deleteEmployee(id) {
  const row = employees.find((e) => e.employeeId === Number(id))
  if (!row || row.delmark) throw new Error('员工不存在')
  row.delmark = 1
  row.accountStatus = 0
  if (row.username && authAccounts[row.username]) {
    delete authAccounts[row.username]
  }
  return enrichEmployee(row)
}
