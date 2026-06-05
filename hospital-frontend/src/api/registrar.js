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
  return request.get('/admin/departments', { params: { deptType: 1, pageSize: 50 } })
}

export function fetchRegistLevels() {
  if (useMock()) return mockRegistLevels()
  return request.get('/admin/regist-levels', { params: { pageSize: 20 } })
}

export function fetchSettleCategories() {
  if (useMock()) return mockSettleCategories()
  return request.get('/admin/settle-categories', { params: { pageSize: 20 } })
}

/** 科室下出诊医生（Mock；真实 API 待定） */
export function fetchDoctorsByDept(deptId) {
  if (useMock()) return mockDoctors(deptId)
  return request.get('/admin/employees', { params: { deptId, roleType: 'OUTPATIENT_DOCTOR' } })
}

/** 排班列表（与小程序 patient/schedules 契约对齐） */
export function fetchRegistrarSchedules(params) {
  if (useMock()) return mockSchedules(params)
  return request.get('/patient/schedules', { params })
}

export function fetchPatientBills(medicalRecordNo, params) {
  if (useMock()) return mockPatientBills(medicalRecordNo, params)
  return request.get(`/registrar/patients/${medicalRecordNo}/bills`, { params })
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
