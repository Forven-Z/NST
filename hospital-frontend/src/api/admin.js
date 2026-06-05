import request from './request'
import { useMock } from '../utils/mock'
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
