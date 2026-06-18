import request from './request'
import { useMock } from '../utils/mock'
import {
  mockDepartments,
  mockDoctors,
  mockPatientBills,
  mockRefundBill,
  mockRegistLevels,
  mockSchedules,
  mockSettleCategories,
  mockWindowCharge,
  mockWindowRegister,
} from '../mock/registrar'

export function fetchOutpatientDepartments() {
  if (useMock()) return mockDepartments()
  return request.get('/registrar/departments')
}

export function fetchRegistLevels() {
  if (useMock()) return mockRegistLevels()
  return request.get('/admin/regist-levels', { params: { pageSize: 20 } })
}

export function fetchSettleCategories() {
  if (useMock()) return mockSettleCategories()
  return request.get('/registrar/settle-categories')
}

/** 科室下出诊医生 */
export function fetchDoctorsByDept(deptId) {
  if (useMock()) return mockDoctors(deptId)
  return request.get('/registrar/doctors', { params: { deptId } })
}

/** 排班列表（窗口挂号页） */
export function fetchRegistrarSchedules(params) {
  if (useMock()) return mockSchedules(params)
  return request.get('/registrar/schedules', { params })
}

export function fetchPatientBills(medicalRecordNo, params) {
  if (useMock()) return mockPatientBillsQuery({ medicalRecordNo, ...params })
  return request.get(`/registrar/patients/${medicalRecordNo}/bills`, { params })
}

/** 收费查账：病历号 / 身份证 / 姓名 / patientId，均为精确匹配（姓名重名返回 candidates） */
export function fetchPatientBillsByQuery(params) {
  if (useMock()) return mockPatientBillsQuery(params)
  return request.get('/registrar/patients/bills', { params })
}

export function refundBill(data) {
  if (useMock()) return mockRefundBill(data)
  return request.post('/registrar/refunds', data)
}

export function cancelRegister(registerId, data) {
  return request.post(`/registrar/registers/${registerId}/cancel`, data)
}

export function windowRegister(data) {
  if (useMock()) return mockWindowRegister(data)
  return request.post('/registrar/registers', data)
}

export function windowCharge(data) {
  if (useMock()) return mockWindowCharge(data)
  return request.post('/registrar/charges', data)
}
