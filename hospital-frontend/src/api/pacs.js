import request from './request'
import { useMock } from '../utils/mock'
import { mockPacsExecute, mockPacsGenerateAiReport, mockPacsQueue, mockPacsSaveResult } from '../mock/pacs'
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

export function generatePacsAiReport(id) {
  if (useMock()) return mockPacsGenerateAiReport(id)
  return request.post(`/pacs/requests/${id}/ai-report`, null, { timeout: 180000 })
}

export function fetchPacsResultDetail(id) {
  return request.get(`/pacs/requests/${id}/result-detail`)
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

async function assertPreviewBlob(blob, kind) {
  const contentType = blob?.type || ''
  if (contentType.includes('json') || (blob?.size < 2048 && contentType !== 'application/gzip')) {
    const text = await blob.text()
    try {
      const json = JSON.parse(text)
      throw new Error(json.message || `${kind} 预览加载失败`)
    } catch (err) {
      if (err instanceof SyntaxError) throw new Error(`${kind} 预览格式无效`)
      throw err
    }
  }
  if (!blob?.size || blob.size < 2048) {
    throw new Error(`${kind} 预览文件过小，请重新分析`)
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
