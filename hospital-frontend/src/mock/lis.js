import { mockResult } from '../utils/mock'
import {
  executeInspection,
  generateTechAiReport,
  getInspectionQueue,
  getTechResultDetail,
  saveInspectionResult,
} from './store'

export function mockLisQueue(params) {
  const list = getInspectionQueue(params?.status ?? 20)
  return mockResult({ list, page: 1, pageSize: 50 })
}

export function mockLisExecute(id) {
  const row = executeInspection(id)
  return mockResult({ inspectionRequestId: row.inspectionRequestId, status: row.status })
}

export function mockLisSaveResult(id, body) {
  const row = saveInspectionResult(id, body)
  return mockResult({
    inspectionRequestId: row.inspectionRequestId,
    status: row.status,
    resultText: row.resultText,
    instrumentData: row.instrumentData,
    aiReportText: row.aiReportText,
    doctorReportText: row.doctorReportText,
  })
}

export function mockLisResultDetail(id) {
  return mockResult(getTechResultDetail('INSPECTION', id))
}

export function mockLisGenerateAiReport(id) {
  return mockResult(generateTechAiReport('INSPECTION', id))
}
