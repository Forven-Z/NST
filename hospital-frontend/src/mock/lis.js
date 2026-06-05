import { mockResult } from '../utils/mock'
import { executeInspection, getInspectionQueue, saveInspectionResult } from './store'

export function mockLisQueue(params) {
  const list = getInspectionQueue(params?.status ?? 20)
  return mockResult({ list, page: 1, pageSize: 50 })
}

export function mockLisExecute(id) {
  const row = executeInspection(id)
  return mockResult({ inspectionRequestId: row.inspectionRequestId, status: row.status })
}

export function mockLisSaveResult(id, body) {
  const row = saveInspectionResult(id, body?.resultText)
  return mockResult({ inspectionRequestId: row.inspectionRequestId, status: row.status, resultText: row.resultText })
}
