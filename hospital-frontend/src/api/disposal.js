import request from './request'
import {
  mockDisposalExecute,
  mockDisposalQueue,
  mockDisposalResult,
} from '../mock/disposal'

/** 处置后端 PENDING：暂始终 Mock，待 zcl 实现后改为 useMock() 分支 */
function disposalPending() {
  return true
}

export function fetchDisposalQueue(params) {
  if (disposalPending()) return mockDisposalQueue(params)
  return request.get('/disposal/queue', { params })
}

export function executeDisposalRequest(id) {
  if (disposalPending()) return mockDisposalExecute(id)
  return request.post(`/disposal/requests/${id}/execute`)
}

export function saveDisposalResult(id, data) {
  if (disposalPending()) return mockDisposalResult(id, data)
  return request.post(`/disposal/requests/${id}/result`, data)
}
