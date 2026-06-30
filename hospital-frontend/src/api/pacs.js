import request from './request'
import { useMock } from '../utils/mock'
import { dataUrlToBlob } from '../utils/reportSnapshot'
import { mockPacsExecute, mockPacsGenerateAiReport, mockPacsGenerateLlmReport, mockPacsQueue, mockPacsResultDetail, mockPacsSaveResult, mockPacsUploadReportSnapshots } from '../mock/pacs'
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
  return request.post(`/pacs/requests/${id}/result`, data)
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
  return request.post(`/pacs/requests/${id}/ai-report`, null, { timeout: 180000 })
}

export function generatePacsLlmReport(id, findingsText) {
  if (useMock()) return mockPacsGenerateLlmReport(id, findingsText)
  return request.post(`/pacs/requests/${id}/llm-report`, { findingsText }, { timeout: 180000 })
}

export function uploadPacsReportSnapshots(checkRequestId, snapshots) {
  if (useMock()) return mockPacsUploadReportSnapshots(checkRequestId, snapshots)
  const form = new FormData()
  for (const plane of ['axial', 'coronal', 'sagittal']) {
    const dataUrl = snapshots?.[plane]
    if (!dataUrl) continue
    const blob = dataUrl.startsWith('data:') ? dataUrlToBlob(dataUrl) : null
    if (blob) form.append(plane, blob, `${plane}.png`)
  }
  if (snapshots?.meta) {
    form.append('meta', JSON.stringify(snapshots.meta))
  }
  return request.post(`/pacs/requests/${checkRequestId}/report-snapshots`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}

export function fetchPacsImagingPreview(id) {
  return request.get(`/pacs/requests/${id}/imaging-preview`)
}

export function uploadPacsImaging(checkRequestId, files, onUploadProgress) {
  const form = new FormData()
  form.append('checkRequestId', String(checkRequestId))
  files.forEach((file) => form.append('files', file))
  return request.post('/pacs/imaging/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
    onUploadProgress,
  })
}

async function assertErrorJsonBlob(blob, label) {
  const contentType = blob?.type || ''
  if (!contentType.includes('json') && (blob?.size ?? 0) >= 512) return
  const text = await blob.text()
  try {
    const json = JSON.parse(text)
    throw new Error(json.message || `${label} 加载失败`)
  } catch (err) {
    if (err instanceof SyntaxError) return
    throw err
  }
}

async function assertPreviewBlob(blob, kind) {
  const contentType = blob?.type || ''
  if (contentType.includes('json') || (blob?.size < 2048 && contentType !== 'application/gzip')) {
    await assertErrorJsonBlob(blob, `${kind} 预览`)
    throw new Error(`${kind} 预览格式无效`)
  }
  if (!blob?.size || blob.size < 2048) {
    throw new Error(`${kind} 预览文件过小，请重新分析`)
  }
}

async function assertReportSnapshotBlob(blob, plane) {
  await assertErrorJsonBlob(blob, `${plane} 报告采图`)
  const contentType = blob?.type || ''
  if (contentType.includes('json')) {
    throw new Error(`${plane} 报告采图格式无效`)
  }
  if (!blob?.size) {
    throw new Error(`${plane} 报告采图为空`)
  }
}

export async function fetchPacsPreviewBlob(checkRequestId, kind) {
  const blob = await request.get(`/pacs/imaging/preview/${checkRequestId}/${kind}`, {
    responseType: 'blob',
    timeout: 120000,
  })
  await assertPreviewBlob(blob, kind)
  return URL.createObjectURL(blob)
}

/** 带 JWT 加载报告三视图采图（供 img 无法携带 Authorization 时使用） */
export async function fetchPacsReportSnapshotBlob(checkRequestId, plane) {
  const blob = await request.get(`/pacs/imaging/report-preview/${checkRequestId}/${plane}`, {
    responseType: 'blob',
    timeout: 60000,
  })
  await assertReportSnapshotBlob(blob, plane)
  return URL.createObjectURL(blob)
}
