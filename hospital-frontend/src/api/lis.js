import request from './request'
import { useMock } from '../utils/mock'
import { mockLisExecute, mockLisQueue, mockLisSaveResult } from '../mock/lis'

export function fetchLisQueue(params) {
  if (useMock()) return mockLisQueue(params)
  return request.get('/lis/queue', { params })
}

export function executeLisRequest(id) {
  if (useMock()) return mockLisExecute(id)
  return request.post(`/lis/requests/${id}/execute`)
}

export function saveLisResult(id, data) {
  if (useMock()) return mockLisSaveResult(id, data)
  return request.post(`/lis/requests/${id}/result`, data)
}
