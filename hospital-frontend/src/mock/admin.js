import { mockResult } from '../utils/mock'
import {
  MOCK_ALL_DEPARTMENTS,
  MOCK_DRUGS,
  MOCK_MEDICAL_TECHNOLOGIES,
  MOCK_OUTPATIENT_DEPTS,
  MOCK_REGIST_LEVELS,
  MOCK_SCHEDULES,
} from './dict'

export function mockAdminDepartments(params) {
  const deptType = params?.deptType
  let list = MOCK_ALL_DEPARTMENTS
  if (deptType !== undefined && deptType !== null && deptType !== '') {
    list = list.filter((d) => d.deptType === Number(deptType))
  }
  return mockResult({ list, page: 1, pageSize: 50 })
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

export function mockAdminSchedules(params) {
  let list = [...MOCK_SCHEDULES]
  if (params?.deptId) list = list.filter((s) => s.deptId === Number(params.deptId))
  return mockResult({ list, page: 1, pageSize: 100 })
}
