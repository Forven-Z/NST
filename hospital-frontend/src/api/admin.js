import request from './request'
import { useMock } from '../utils/mock'
import {
  mockAiSchedulingSuggest,
  mockCreateAdminSchedule,
  mockPublishAdminSchedule,
  mockUpdateAdminSchedule,
} from '../mock/admin-ai'
import {
  mockAdminDepartmentDetail,
  mockAdminDepartments,
  mockAdminEmployeeDetail,
  mockAdminEmployees,
  mockAdminDrugs,
  mockAdminMedicalTechnologies,
  mockAdminRegistLevels,
  mockAdminSchedules,
  mockCreateDepartment,
  mockCreateEmployee,
  mockDeleteDepartment,
  mockDeleteEmployee,
  mockUpdateDepartment,
  mockUpdateEmployee,
} from '../mock/admin'

export function fetchDepartments(params) {
  if (useMock()) return mockAdminDepartments(params)
  return request.get('/admin/departments', { params })
}

export function fetchDepartment(id) {
  if (useMock()) return mockAdminDepartmentDetail(id)
  return request.get(`/admin/departments/${id}`)
}

export function createDepartment(data) {
  if (useMock()) return mockCreateDepartment(data)
  return request.post('/admin/departments', data)
}

export function updateDepartment(id, data) {
  if (useMock()) return mockUpdateDepartment(id, data)
  return request.put(`/admin/departments/${id}`, data)
}

export function deleteDepartment(id) {
  if (useMock()) return mockDeleteDepartment(id)
  return request.delete(`/admin/departments/${id}`)
}

export function fetchEmployees(params) {
  if (useMock()) return mockAdminEmployees(params)
  return request.get('/admin/employees', { params })
}

export function fetchEmployee(id) {
  if (useMock()) return mockAdminEmployeeDetail(id)
  return request.get(`/admin/employees/${id}`)
}

export function createEmployee(data) {
  if (useMock()) return mockCreateEmployee(data)
  return request.post('/admin/employees', data)
}

export function updateEmployee(id, data) {
  if (useMock()) return mockUpdateEmployee(id, data)
  return request.put(`/admin/employees/${id}`, data)
}

export function deleteEmployee(id) {
  if (useMock()) return mockDeleteEmployee(id)
  return request.delete(`/admin/employees/${id}`)
}

export function fetchRegistLevels(params) {
  if (useMock()) return mockAdminRegistLevels(params)
  return request.get('/admin/regist-levels', { params })
}

export function fetchDrugs(params) {
  if (useMock()) return mockAdminDrugs(params)
  return request.get('/admin/drugs', { params })
}

export function fetchMedicalTechnologies(params) {
  if (useMock()) return mockAdminMedicalTechnologies(params)
  return request.get('/admin/medical-technologies', { params })
}

export function fetchAdminSchedules(params) {
  if (useMock()) return mockAdminSchedules(params)
  return request.get('/admin/schedules', { params })
}

export function createAdminSchedule(data) {
  if (useMock()) return mockCreateAdminSchedule(data)
  return request.post('/admin/schedules', data)
}

export function publishAdminSchedule(schedulingId) {
  if (useMock()) return mockPublishAdminSchedule(schedulingId)
  return request.post(`/admin/schedules/${schedulingId}/publish`)
}

export function fetchAiSchedulingSuggest(params) {
  if (useMock()) return mockAiSchedulingSuggest(params)
  return request.post('/admin/schedules/ai-suggest', params || {})
}

export function updateAdminSchedule(schedulingId, data) {
  if (useMock()) return mockUpdateAdminSchedule(schedulingId, data)
  return request.put(`/admin/schedules/${schedulingId}`, data)
}
