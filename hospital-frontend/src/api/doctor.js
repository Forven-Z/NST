import request from './request'
import { useMock } from '../utils/mock'
import {
  mockCallPatient,
  mockCreateCheckOrder,
  mockCreateDisposalOrder,
  mockCreateInspectionOrder,
  mockCreatePrescription,
  mockResubmitPrescription,
  mockUpdatePrescription,
  mockDoctorQueue,
  mockFetchCheckResult,
  mockFetchDisposalResult,
  mockFetchInspectionResult,
  mockFetchMedicalRecord,
  mockFetchRegisterOrders,
  mockFetchPatientVisits,
  mockFetchPatientVisitHub,
  mockFetchPatientVisitOrderResult,
  mockConfirmMedicalRecord,
  mockFetchRegisterResults,
  mockFinishVisit,
  mockDoctorMedicalTechnologies,
  mockDoctorDrugs,
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
  return request.post(`/doctor/call/${registerId}`)
}

export function finishVisit(registerId) {
  if (useMock()) return mockFinishVisit(registerId)
  return request.post(`/doctor/registers/${registerId}/finish`)
}

export function fetchMedicalRecord(registerId) {
  if (useMock()) return mockFetchMedicalRecord(registerId)
  return request.get(`/doctor/medical-records/${registerId}`)
}

export function saveMedicalRecord(registerId, data) {
  if (useMock()) return mockSaveMedicalRecord(registerId, data)
  return request.put(`/doctor/medical-records/${registerId}`, data)
}

export function confirmMedicalRecord(registerId, data) {
  if (useMock()) return mockConfirmMedicalRecord(registerId, data)
  return request.post(`/doctor/medical-records/${registerId}/submit`, data)
}

export function fetchDiseases(params) {
  if (useMock()) return mockResult({ list: MOCK_DISEASES, page: 1, pageSize: 50 })
  return request.get('/doctor/diseases', { params })
}

export function fetchMedicalTechnologies(params) {
  if (useMock()) return mockDoctorMedicalTechnologies(params)
  return request.get('/doctor/medical-technologies', { params })
}

export function fetchDrugs(params) {
  if (useMock()) return mockDoctorDrugs(params)
  return request.get('/doctor/drugs', { params })
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

const ORDER_RESULT_FETCHERS = {
  inspection: fetchInspectionResult,
  check: fetchCheckResult,
  disposal: fetchDisposalResult,
}

/** 医生工作站查看单条医技结果（检验/检查/处置） */
export function fetchOrderResult(kind, requestId) {
  const fetcher = ORDER_RESULT_FETCHERS[kind]
  if (!fetcher) {
    return Promise.reject(new Error('不支持的结果类型'))
  }
  return fetcher(requestId)
}

export function fetchRegisterOrders(registerId) {
  if (useMock()) return mockFetchRegisterOrders(registerId)
  return request.get(`/doctor/registers/${registerId}/orders`)
}

export function fetchPatientVisits(patientId, params) {
  if (useMock()) return mockFetchPatientVisits(patientId, params)
  return request.get(`/doctor/patients/${patientId}/visits`, { params })
}

export function fetchPatientVisitHub(patientId, registerId) {
  if (useMock()) return mockFetchPatientVisitHub(patientId, registerId)
  return request.get(`/doctor/patients/${patientId}/visits/${registerId}/hub`)
}

export function fetchPatientVisitOrderResult(patientId, kind, requestId) {
  if (useMock()) return mockFetchPatientVisitOrderResult(patientId, kind, requestId)
  return request.get(`/doctor/patients/${patientId}/order-results/${kind}/${requestId}`)
}

const REQUEST_ID_KEYS = {
  inspection: 'inspectionRequestId',
  check: 'checkRequestId',
  disposal: 'disposalRequestId',
}

/** 从 orders 响应组装医技结果（后端无聚合 /results 接口，见 docs/API.md） */
export function buildRegisterResultsFromOrders(ordersData) {
  const list = ordersData?.list ?? []
  const detailByKind = {
    inspection: ordersData?.inspections ?? [],
    check: ordersData?.checks ?? [],
    disposal: ordersData?.disposals ?? [],
  }
  const results = []
  for (const item of list) {
    if (item.kind === 'prescription' || item.status < 40) continue
    const idKey = REQUEST_ID_KEYS[item.kind]
    if (!idKey) continue
    const detail = detailByKind[item.kind].find((r) => r[idKey] === item.requestId)
    if (!detail?.resultText) continue
    results.push({
      kind: item.kind,
      requestId: item.requestId,
      typeLabel: item.typeLabel,
      itemName: item.itemName,
      resultText: detail.resultText,
      reportTime: detail.resultTime ?? detail.reportTime,
    })
  }
  return results
}

export async function fetchRegisterResults(registerId) {
  if (useMock()) return mockFetchRegisterResults(registerId)
  const ordersRes = await request.get(`/doctor/registers/${registerId}/orders`)
  const results = buildRegisterResultsFromOrders(ordersRes.data ?? {})
  return { ...ordersRes, data: { registerId: Number(registerId), results } }
}

export function createPrescription(data) {
  if (useMock()) return mockCreatePrescription(data)
  return request.post('/doctor/prescriptions', data)
}

export function updatePrescription(prescriptionId, data) {
  if (useMock()) return mockUpdatePrescription(prescriptionId, data)
  return request.put(`/doctor/prescriptions/${prescriptionId}`, data)
}

export function resubmitPrescription(prescriptionId) {
  if (useMock()) return mockResubmitPrescription(prescriptionId)
  return request.post(`/doctor/prescriptions/${prescriptionId}/resubmit`)
}

export function fetchDiagnosisSuggest(data) {
  if (useMock()) return mockDiagnosisSuggest(data)
  return request.post('/ai/diagnosis/suggest', data)
}

export function createCheckAiDraft(data) {
  if (useMock()) return mockClinicalAiDraft('CHECK', data.registerId)
  return request.post('/ai/doctor/check-requests/ai-draft', data)
}

export function createInspectionAiDraft(data) {
  if (useMock()) return mockClinicalAiDraft('INSPECTION', data.registerId)
  return request.post('/ai/doctor/inspection-requests/ai-draft', data)
}

export function createDisposalAiDraft(data) {
  if (useMock()) return mockClinicalAiDraft('DISPOSAL', data.registerId)
  return request.post('/ai/doctor/disposal-requests/ai-draft', data)
}

export function confirmCheckAiDraft(draftId) {
  if (useMock()) return mockConfirmAiDraft('CHECK', draftId)
  return request.post(`/ai/doctor/check-requests/ai-draft/${draftId}/confirm`)
}

export function confirmInspectionAiDraft(draftId) {
  if (useMock()) return mockConfirmAiDraft('INSPECTION', draftId)
  return request.post(`/ai/doctor/inspection-requests/ai-draft/${draftId}/confirm`)
}

export function confirmDisposalAiDraft(draftId) {
  if (useMock()) return mockConfirmAiDraft('DISPOSAL', draftId)
  return request.post(`/ai/doctor/disposal-requests/ai-draft/${draftId}/confirm`)
}

export function updateCheckAiDraft(draftId, data) {
  if (useMock()) return mockUpdateClinicalAiDraft('CHECK', draftId, data)
  return request.put(`/ai/doctor/check-requests/ai-draft/${draftId}`, data)
}

export function updateInspectionAiDraft(draftId, data) {
  if (useMock()) return mockUpdateClinicalAiDraft('INSPECTION', draftId, data)
  return request.put(`/ai/doctor/inspection-requests/ai-draft/${draftId}`, data)
}

export function updateDisposalAiDraft(draftId, data) {
  if (useMock()) return mockUpdateClinicalAiDraft('DISPOSAL', draftId, data)
  return request.put(`/ai/doctor/disposal-requests/ai-draft/${draftId}`, data)
}

export function createPrescriptionAiDraft(data) {
  if (useMock()) return mockPrescriptionAiDraft(data.registerId)
  return request.post('/ai/doctor/prescriptions/ai-draft', data)
}

export function updatePrescriptionAiDraft(draftId, data) {
  if (useMock()) return mockUpdatePrescriptionAiDraft(draftId, data)
  return request.put(`/ai/doctor/prescriptions/ai-draft/${draftId}`, data)
}
