import request from './request'
import { useMock } from '../utils/mock'
import { mockDisposalExecute, mockDisposalGenerateAiReport, mockDisposalQueue, mockDisposalResult } from '../mock/disposal'

export function fetchDisposalQueue(params) {
  if (useMock()) return mockDisposalQueue(params)
  return request.get('/disposal/queue', { params })
}

export function executeDisposalRequest(id) {
  if (useMock()) return mockDisposalExecute(id)
  return request.post(`/disposal/requests/${id}/execute`)
}

export function saveDisposalResult(id, data) {
  if (useMock()) return mockDisposalResult(id, data)
  return request.post(`/disposal/requests/${id}/result`, data)
}

export function generateDisposalAiReport(id) {
  if (useMock()) return mockDisposalGenerateAiReport(id)
  return request.post(`/disposal/requests/${id}/ai-report`)
}
