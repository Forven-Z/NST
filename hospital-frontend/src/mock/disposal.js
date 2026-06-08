import { mockResult } from '../utils/mock'
import {
  executeDisposal,
  generateTechAiReport,
  getDisposalQueue,
  getTechResultDetail,
  saveDisposalResult,
} from './store'

export function mockDisposalQueue(params) {
  const list = getDisposalQueue(params?.status ?? 20)
  return mockResult({ list, page: 1, pageSize: 20 })
}

export function mockDisposalExecute(id) {
  const row = executeDisposal(id)
  return mockResult({ disposalRequestId: row.disposalRequestId, status: row.status })
}

export function mockDisposalResult(id, body) {
  const row = saveDisposalResult(id, body)
  return mockResult({
    disposalRequestId: row.disposalRequestId,
    status: row.status,
    resultText: row.resultText,
    instrumentData: row.instrumentData,
    aiReportText: row.aiReportText,
    doctorReportText: row.doctorReportText,
  })
}

export function mockDisposalResultDetail(id) {
  return mockResult(getTechResultDetail('DISPOSAL', id))
}

export function mockDisposalGenerateAiReport(id) {
  return mockResult(generateTechAiReport('DISPOSAL', id))
}
