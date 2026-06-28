import request from './request'
import { useMock } from '../utils/mock'
import {
  mockAiSchedulingSuggest,
  mockApplyScheduleTemplate,
  mockBatchPublishSchedules,
  mockBatchUpsertSchedules,
  mockCopyScheduleWeek,
  mockCreateAdminSchedule,
  mockFetchScheduleTemplate,
  mockPublishAdminSchedule,
  mockReplaceScheduleTemplate,
  mockUpdateAdminSchedule,
  mockWeekGrid,
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
  mockFinanceDailySummary,
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
  return request.get('/admin/scheduling', { params })
}

export function createAdminSchedule(data) {
  if (useMock()) return mockCreateAdminSchedule(data)
  const { employeeId, registLevelId, workDate, noonType, totalQuota } = data
  return request.post('/admin/scheduling', { employeeId, registLevelId, workDate, noonType, totalQuota })
}

export function publishAdminSchedule(schedulingId) {
  if (useMock()) return mockPublishAdminSchedule(schedulingId)
  return request.post(`/admin/scheduling/${schedulingId}/publish`)
}

export function fetchAiSchedulingSuggest(params) {
  if (useMock()) return mockAiSchedulingSuggest(params)
  return request.post('/admin/scheduling/ai-suggest', params || {})
}

export function applyAiSchedulingReplace(schedulingId, data) {
  if (useMock()) return mockUpdateAdminSchedule(schedulingId, data)
  return request.post(`/admin/scheduling/${schedulingId}/ai-replace`, data || {})
}

export function updateAdminSchedule(schedulingId, data) {
  if (useMock()) return mockUpdateAdminSchedule(schedulingId, data)
  const payload = {}
  if (data.employeeId != null) payload.employeeId = data.employeeId
  if (data.totalQuota != null) payload.totalQuota = data.totalQuota
  if (data.publishStatus != null) payload.publishStatus = data.publishStatus
  return request.put(`/admin/scheduling/${schedulingId}`, payload)
}

export function fetchFinanceDailySummary(params) {
  if (useMock()) return mockFinanceDailySummary(params)
  return request.get('/admin/finance/daily-summary', { params })
}

export function fetchWeekGrid(params) {
  if (useMock()) return mockWeekGrid(params)
  return request.get('/admin/scheduling/week-grid', { params })
}

export function batchUpsertSchedules(data) {
  if (useMock()) return mockBatchUpsertSchedules(data)
  return request.post('/admin/scheduling/batch-upsert', data)
}

export function copyScheduleWeek(data) {
  if (useMock()) return mockCopyScheduleWeek(data)
  return request.post('/admin/scheduling/copy-week', data)
}

export function applyScheduleTemplate(data) {
  if (useMock()) return mockApplyScheduleTemplate(data)
  return request.post('/admin/scheduling/apply-template', data)
}

export function batchPublishSchedules(data) {
  if (useMock()) return mockBatchPublishSchedules(data)
  return request.post('/admin/scheduling/batch-publish', data)
}

export function fetchScheduleTemplate(employeeId) {
  if (useMock()) return mockFetchScheduleTemplate(employeeId)
  return request.get(`/admin/scheduling/templates/${employeeId}`)
}

export function replaceScheduleTemplate(employeeId, data) {
  if (useMock()) return mockReplaceScheduleTemplate(employeeId, data)
  return request.put(`/admin/scheduling/templates/${employeeId}`, data)
}

