import { mockResult } from '../utils/mock'
import { enrichScheduleAdminRow } from './scheduling-leave'
import {
  MOCK_DRUGS,
  MOCK_MEDICAL_TECHNOLOGIES,
  MOCK_REGIST_LEVELS,
  MOCK_SCHEDULES,
} from './dict'
import {
  createDepartment,
  createEmployee,
  deleteDepartment,
  deleteEmployee,
  getDepartmentById,
  getEmployeeById,
  listDepartments,
  listEmployees,
  updateDepartment,
  updateEmployee,
} from './staff-registry'

export function mockAdminDepartments(params) {
  return mockResult({ list: listDepartments(params), page: 1, pageSize: 50 })
}

export function mockAdminDepartmentDetail(id) {
  const row = getDepartmentById(id)
  if (!row || row.delmark) throw new Error('科室不存在')
  return mockResult(row)
}

export function mockCreateDepartment(data) {
  return mockResult({ ...createDepartment(data), message: '科室已创建' })
}

export function mockUpdateDepartment(id, data) {
  return mockResult({ ...updateDepartment(id, data), message: '科室已更新' })
}

export function mockDeleteDepartment(id) {
  deleteDepartment(id)
  return mockResult({ message: '科室已停用' })
}

export function mockAdminEmployees(params) {
  return mockResult({ list: listEmployees(params), page: 1, pageSize: 100 })
}

export function mockAdminEmployeeDetail(id) {
  const row = getEmployeeById(id)
  if (!row) throw new Error('员工不存在')
  return mockResult(row)
}

export function mockCreateEmployee(data) {
  const row = createEmployee(data)
  return mockResult({
    ...row,
    message: `员工已建档，可使用 ${row.username} / ${data.password || '123456'} 登录`,
  })
}

export function mockUpdateEmployee(id, data) {
  return mockResult({ ...updateEmployee(id, data), message: '员工信息已更新' })
}

export function mockDeleteEmployee(id) {
  return mockResult({ ...deleteEmployee(id), message: '员工已停用，登录已关闭' })
}

export function mockAdminRegistLevels() {
  return mockResult({
    list: MOCK_REGIST_LEVELS.map((l) => ({ ...l, fee: l.registFee })),
    page: 1,
    pageSize: 20,
  })
}

export function mockAdminDrugs() {
  return mockResult({ list: MOCK_DRUGS, page: 1, pageSize: 50 })
}

export function mockAdminMedicalTechnologies() {
  return mockResult({ list: MOCK_MEDICAL_TECHNOLOGIES, page: 1, pageSize: 50 })
}

function sortSchedulesByDate(list) {
  return [...list].sort((a, b) => {
    const dateCmp = String(a.workDate || '').localeCompare(String(b.workDate || ''))
    if (dateCmp !== 0) return dateCmp
    return (a.noonType ?? 0) - (b.noonType ?? 0)
  })
}

export function mockAdminSchedules(params) {
  let list = [...MOCK_SCHEDULES].filter((s) => (s.publishStatus ?? 1) !== 2)
  if (params?.deptId) list = list.filter((s) => s.deptId === Number(params.deptId))
  list = sortSchedulesByDate(list)
  return mockResult({ list: list.map(enrichScheduleAdminRow), page: 1, pageSize: 100 })
}
