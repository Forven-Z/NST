import request from './request'
import { useMock } from '../utils/mock'
import { mockPacsExecute, mockPacsQueue, mockPacsSaveResult } from '../mock/pacs'

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
  return request.post(`/pacs/requests/${id}/result`, data)
}
