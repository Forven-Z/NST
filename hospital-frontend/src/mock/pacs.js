import { mockResult } from '../utils/mock'
import {
  executeCheck,
  generateTechAiReport,
  getCheckQueue,
  getTechResultDetail,
  saveCheckReportSnapshots,
  saveCheckResult,
} from './store'

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
  })
}

export function mockPacsResultDetail(id) {
  return mockResult(getTechResultDetail('CHECK', id))
}

/** CNN 影像推理（影像 AI 工作台） */
export function mockPacsGenerateAiReport(id) {
  const row = executeCheck(id)
  if (row.status < 30) {
    row.status = 30
  }
  row.studyStatus = 'COMPLETED'
  return mockResult(getTechResultDetail('CHECK', id))
}

/** LLM 报告生成（报告单下方诊断印象，须传入 CT 所见） */
export function mockPacsGenerateLlmReport(id, findingsText) {
  return mockResult(generateTechAiReport('CHECK', id, findingsText))
}

export function mockPacsUploadReportSnapshots(id, snapshots) {
  saveCheckReportSnapshots(id, snapshots)
  return mockResult(getTechResultDetail('CHECK', id))
}
