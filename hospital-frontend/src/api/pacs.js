import request from './request'
import { useMock } from '../utils/mock'
import {
  mockPacsExecute,
  mockPacsGenerateAiReport,
  mockPacsQueue,
  mockPacsResultDetail,
  mockPacsSaveResult,
} from '../mock/pacs'
import { mockImagingStudies } from '../mock/pacs-imaging'

export function fetchPacsQueue(params) {
  if (useMock()) return mockPacsQueue(params)
  return request.get('/pacs/queue', { params })
}

export function executePacsRequest(id) {
  if (useMock()) return mockPacsExecute(id)
  return request.post(`/pacs/requests/${id}/execute`)
}

export function savePacsResult(id, data) {
  if (useMock()) return mockPacsSaveResult(id, data)
  return request.put(`/pacs/requests/${id}/result`, data)
}

export function fetchImagingStudies(params) {
  if (useMock()) return mockImagingStudies(params)
  return request.get('/pacs/imaging-studies', { params })
}

export function fetchPacsResultDetail(id) {
  if (useMock()) return mockPacsResultDetail(id)
  return request.get(`/pacs/requests/${id}/result-detail`)
}

export function generatePacsAiReport(id) {
  if (useMock()) return mockPacsGenerateAiReport(id)
  return request.post(`/pacs/requests/${id}/ai-report`)
}
