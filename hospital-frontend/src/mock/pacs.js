import { mockResult } from '../utils/mock'
import { executeCheck, generateTechAiReport, getCheckQueue, getTechResultDetail, saveCheckResult } from './store'

export function mockPacsQueue(params) {
  const list = getCheckQueue(params?.status ?? 20)
  return mockResult({ list, page: 1, pageSize: 50 })
}

export function mockPacsExecute(id) {
  const row = executeCheck(id)
  return mockResult({ checkRequestId: row.checkRequestId, status: row.status })
}

export function mockPacsSaveResult(id, body) {
  const row = saveCheckResult(id, body)
  return mockResult({
    checkRequestId: row.checkRequestId,
    status: row.status,
    resultText: row.resultText,
    instrumentData: row.instrumentData,
    aiReportText: row.aiReportText,
    doctorReportText: row.doctorReportText,
  })
}

export function mockPacsResultDetail(id) {
  return mockResult(getTechResultDetail('CHECK', id))
}

export function mockPacsGenerateAiReport(id) {
  return mockResult(generateTechAiReport('CHECK', id))
}
