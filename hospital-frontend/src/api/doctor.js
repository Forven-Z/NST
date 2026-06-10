import request from './request'
import { useMock } from '../utils/mock'
import {
  mockCallPatient,
  mockCreateCheckOrder,
  mockCreateDisposalOrder,
  mockCreateInspectionOrder,
  mockCreatePrescription,
  mockDoctorQueue,
  mockFetchCheckResult,
  mockFetchDisposalResult,
  mockFetchInspectionResult,
  mockFetchMedicalRecord,
  mockFetchRegisterOrders,
  mockConfirmMedicalRecord,
  mockFetchRegisterResults,
  mockConfirmMedicalRecord,
  mockFinishVisit,
  mockSaveMedicalRecord,
} from '../mock/doctor'
import { MOCK_DISEASES } from '../mock/dict'
import {
  mockClinicalAiDraft,
  mockConfirmAiDraft,
  mockDiagnosisSuggest,
  mockPrescriptionAiDraft,
  mockUpdateClinicalAiDraft,
  mockUpdatePrescriptionAiDraft,
} from '../mock/ai'
import { mockResult } from '../utils/mock'

export function fetchDoctorQueue(params) {
  if (useMock()) return mockDoctorQueue(params)
  return request.get('/doctor/queues', { params })
}

export function callPatient(registerId) {
  if (useMock()) return mockCallPatient(registerId)
  return request.post(`/doctor/registers/${registerId}/call`)
}

export function finishVisit(registerId) {
  if (useMock()) return mockFinishVisit(registerId)
  return request.post(`/doctor/registers/${registerId}/finish`)
}

export function fetchMedicalRecord(registerId) {
  if (useMock()) return mockFetchMedicalRecord(registerId)
  return request.get(`/doctor/registers/${registerId}/medical-record`)
}

export function saveMedicalRecord(registerId, data) {
  if (useMock()) return mockSaveMedicalRecord(registerId, data)
  return request.put(`/doctor/medical-records/${registerId}`, data)
}

export function confirmMedicalRecord(registerId, data) {
  if (useMock()) return mockConfirmMedicalRecord(registerId, data)
  return request.post(`/doctor/registers/${registerId}/medical-record/confirm`, data)
}

export function fetchDiseases(params) {
  if (useMock()) return mockResult({ list: MOCK_DISEASES, page: 1, pageSize: 50 })
  return request.get('/doctor/diseases', { params })
}

export function createInspectionOrder(data) {
  if (useMock()) return mockCreateInspectionOrder(data)
  return request.post('/doctor/inspection-requests', data)
}

export function createCheckOrder(data) {
  if (useMock()) return mockCreateCheckOrder(data)
  return request.post('/doctor/check-requests', data)
}

export function createDisposalOrder(data) {
  if (useMock()) return mockCreateDisposalOrder(data)
  return request.post('/doctor/disposal-requests', data)
}

/** @deprecated 请使用 fetchRegisterResults 聚合接口 */
export function fetchInspectionResult(inspectionRequestId) {
  if (useMock()) return mockFetchInspectionResult(inspectionRequestId)
  return request.get(`/doctor/inspection-requests/${inspectionRequestId}/result`)
}

/** @deprecated 请使用 fetchRegisterResults */
export function fetchCheckResult(checkRequestId) {
  if (useMock()) return mockFetchCheckResult(checkRequestId)
  return request.get(`/doctor/check-requests/${checkRequestId}/result`)
}

/** @deprecated 请使用 fetchRegisterResults */
export function fetchDisposalResult(disposalRequestId) {
  if (useMock()) return mockFetchDisposalResult(disposalRequestId)
  return request.get(`/doctor/disposal-requests/${disposalRequestId}/result`)
}

export function fetchRegisterOrders(registerId) {
  if (useMock()) return mockFetchRegisterOrders(registerId)
  return request.get(`/doctor/registers/${registerId}/orders`)
}

export function fetchRegisterResults(registerId) {
  if (useMock()) return mockFetchRegisterResults(registerId)
  return request.get(`/doctor/registers/${registerId}/results`)
}

export function createPrescription(data) {
  if (useMock()) return mockCreatePrescription(data)
  return request.post('/doctor/prescriptions', data)
}

export function fetchDiagnosisSuggest(data) {
  if (useMock()) return mockDiagnosisSuggest(data)
  return request.post('/ai/diagnosis/suggest', data)
}

export function createCheckAiDraft(data) {
  if (useMock()) return mockClinicalAiDraft('CHECK', data.registerId)
  return request.post('/doctor/check-requests/ai-draft', data)
}

export function createInspectionAiDraft(data) {
  if (useMock()) return mockClinicalAiDraft('INSPECTION', data.registerId)
  return request.post('/doctor/inspection-requests/ai-draft', data)
}

export function createDisposalAiDraft(data) {
  if (useMock()) return mockClinicalAiDraft('DISPOSAL', data.registerId)
  return request.post('/doctor/disposal-requests/ai-draft', data)
}

export function confirmCheckAiDraft(draftId) {
  if (useMock()) return mockConfirmAiDraft('CHECK', draftId)
  return request.post(`/doctor/check-requests/ai-draft/${draftId}/confirm`)
}

export function confirmInspectionAiDraft(draftId) {
  if (useMock()) return mockConfirmAiDraft('INSPECTION', draftId)
  return request.post(`/doctor/inspection-requests/ai-draft/${draftId}/confirm`)
}

export function confirmDisposalAiDraft(draftId) {
  if (useMock()) return mockConfirmAiDraft('DISPOSAL', draftId)
  return request.post(`/doctor/disposal-requests/ai-draft/${draftId}/confirm`)
}

export function updateCheckAiDraft(draftId, data) {
  if (useMock()) return mockUpdateClinicalAiDraft('CHECK', draftId, data)
  return request.put(`/doctor/check-requests/ai-draft/${draftId}`, data)
}

export function updateInspectionAiDraft(draftId, data) {
  if (useMock()) return mockUpdateClinicalAiDraft('INSPECTION', draftId, data)
  return request.put(`/doctor/inspection-requests/ai-draft/${draftId}`, data)
}

export function updateDisposalAiDraft(draftId, data) {
  if (useMock()) return mockUpdateClinicalAiDraft('DISPOSAL', draftId, data)
  return request.put(`/doctor/disposal-requests/ai-draft/${draftId}`, data)
}

export function createPrescriptionAiDraft(data) {
  if (useMock()) return mockPrescriptionAiDraft(data.registerId)
  return request.post('/doctor/prescriptions/ai-draft', data)
}

export function updatePrescriptionAiDraft(draftId, data) {
  if (useMock()) return mockUpdatePrescriptionAiDraft(draftId, data)
  return request.put(`/doctor/prescriptions/ai-draft/${draftId}`, data)
}
