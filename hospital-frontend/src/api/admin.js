import request from './request'
import { useMock } from '../utils/mock'
import {
  mockAiSchedulingSuggest,
  mockApplyAiSchedulingReplace,
  mockUpdateAdminSchedule,
} from '../mock/admin-ai'
import {
  mockAdminDepartments,
  mockAdminDrugs,
  mockAdminMedicalTechnologies,
  mockAdminRegistLevels,
  mockAdminSchedules,
} from '../mock/admin'

export function fetchDepartments(params) {
  if (useMock()) return mockAdminDepartments(params)
  return request.get('/admin/departments', { params })
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

/** AI 智能排班建议（智能体组实现，前端仅展示） */
export function fetchAiSchedulingSuggest(params) {
  if (useMock()) return mockAiSchedulingSuggest(params)
  return request.post('/admin/scheduling/ai-suggest', params || {})
}

/** 应用 AI 推荐排班替换当前排班 */
export function applyAiSchedulingReplace(schedulingId, proposedSchedule) {
  if (useMock()) return mockApplyAiSchedulingReplace(schedulingId, proposedSchedule)
  return request.post(`/admin/scheduling/${schedulingId}/ai-replace`, proposedSchedule)
}

/** 手工编辑排班 */
export function updateAdminSchedule(schedulingId, data) {
  if (useMock()) return mockUpdateAdminSchedule(schedulingId, data)
  return request.put(`/admin/scheduling/${schedulingId}`, data)
}
