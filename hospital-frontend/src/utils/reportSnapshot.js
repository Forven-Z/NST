/** 将 dataURL 转为 Blob（用于上传报告采图） */
export function dataUrlToBlob(dataUrl) {
  if (!dataUrl) return null
  const parts = dataUrl.split(',')
  if (parts.length < 2) return null
  const mime = parts[0].match(/:(.*?);/)?.[1] || 'image/png'
  const binary = atob(parts[1])
  const array = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    array[i] = binary.charCodeAt(i)
  }
  return new Blob([array], { type: mime })
}

export function reportSnapshotPreviewUrl(checkRequestId, plane, base = '') {
  const prefix = base || '/api/v1'
  return `${prefix}/pacs/imaging/report-preview/${checkRequestId}/${plane}`
}

/** 解析需鉴权加载的报告采图 API 路径 */
export function parseReportSnapshotUrl(src) {
  if (!src || typeof src !== 'string') return null
  const match = src.match(/report-preview\/(\d+)\/(axial|coronal|sagittal)/i)
  if (!match) return null
  return { checkRequestId: Number(match[1]), plane: match[2].toLowerCase() }
}

export function isInlineSnapshotSrc(src) {
  return !!src && (src.startsWith('data:') || src.startsWith('blob:'))
}
