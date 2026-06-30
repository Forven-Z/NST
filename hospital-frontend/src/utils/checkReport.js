/**
 * 检查报告视图拼接（与后端 CheckReportComposer 字段对齐）
 */

export function composeCheckResultText(findingsText = '', aiReportText = '', doctorReportText = '') {
  const findings = (findingsText || '').trim()
  const ai = (aiReportText || '').trim()
  const doctor = (doctorReportText || '').trim()
  const parts = []
  if (findings) parts.push('【检查所见】', findings)
  if (ai) parts.push('', `AI：${ai}`)
  if (doctor) parts.push('', `医师：${doctor}`)
  return parts.join('\n').trim()
}

export function parseCheckResultText(resultText) {
  const text = (resultText || '').trim()
  if (!text) return { findingsText: '', aiReportText: '', doctorReportText: '' }

  let findings = ''
  let remainder = text
  const findingsMatch = /^【检查所见】([\s\S]*?)(?:\nAI：|$)/.exec(text)
  if (findingsMatch) {
    findings = (findingsMatch[1] || '').trim()
    const aiIdx = text.indexOf('\nAI：')
    remainder = aiIdx >= 0 ? text.slice(aiIdx + 1) : ''
  }

  const legacy = /^AI：([\s\S]*?)(?:\n医师：([\s\S]*))?$/.exec(remainder)
  if (legacy) {
    return {
      findingsText: findings,
      aiReportText: (legacy[1] || '').trim(),
      doctorReportText: (legacy[2] || '').trim(),
    }
  }
  return { findingsText: findings || text, aiReportText: '', doctorReportText: '' }
}

export function generateCheckAiReportStub(itemName, findingsText = '') {
  const findings = (findingsText || '').trim()
  if (!findings) return ''

  const benign =
    /^(无异常|未见异常|未见明显异常|未见明确异常)$/.test(findings.replace(/\s+/g, '')) ||
    findings.includes('未见明显异常') ||
    findings.includes('未见明确异常')

  let body = ''
  if (benign) {
    const name = itemName || ''
    if (/胸|肺/.test(name)) {
      body =
        '双肺野清晰，纵隔居中，心影大小在正常范围；未见明显急性渗出或占位征象。'
    } else if (/头|颅|脑/.test(name)) {
      body =
        '颅脑 CT 平扫：脑实质密度未见明显异常；脑室系统大小形态正常；中线结构居中；未见明显急性出血或占位征象。'
    } else {
      body = '结合 CT 所见，未见明确异常征象。'
    }
  } else {
    const snippet = findings.length <= 500 ? findings : `${findings.slice(0, 500)}…`
    body = `结合 CT 所见：${snippet}`
  }

  const hint = /X线|X 线|DR/.test(itemName || '')
    ? '请放射科医师结合临床审核签阅。'
    : '请检查医师结合临床审核签阅。'

  return `${body}\n\nAI 提示：${hint}`
}

/** 去掉 legacy「【诊断印象】」标题，UI 区块标题已单独展示 */
export function normalizeCheckAiReportText(text = '') {
  return (text || '').trim().replace(/^【诊断印象】\s*\n?/, '')
}

function hasReportImages(images) {
  if (!images || typeof images !== 'object') return false
  return ['axial', 'coronal', 'sagittal'].some((plane) => images[plane])
}

/** 生成 LLM 报告后保留 CT 所见、三视图 URL 与本地采图 */
export function mergeCheckReportAfterLlm(current, generated) {
  const preservedFindings = current?.findings?.findingsText ?? current?.findingsText ?? ''
  const preservedSnapshots = current?.findings?.localSnapshots
  const preservedMeta = current?.findings?.snapshotMeta
  const preservedImages = hasReportImages(current?.findings?.reportImages)
    ? current.findings.reportImages
    : hasReportImages(generated?.findings?.reportImages)
      ? generated.findings.reportImages
      : null
  return {
    ...generated,
    findings: {
      ...(generated.findings || {}),
      findingsText: preservedFindings || generated.findings?.findingsText || '',
      ...(preservedSnapshots ? { localSnapshots: preservedSnapshots } : {}),
      ...(preservedMeta ? { snapshotMeta: preservedMeta } : {}),
      ...(preservedImages ? { reportImages: preservedImages } : {}),
    },
    findingsText: preservedFindings || generated.findingsText || '',
  }
}

export function composeCheckReportView(context = {}, findings = {}, analysis = {}) {
  const itemName = context.itemName || ''
  const findingsText = (findings.findingsText || context.findingsText || '').trim()
  const ai = normalizeCheckAiReportText(analysis.aiReportText || context.aiReportText || '')
  const doctor = (analysis.doctorReportText || context.doctorReportText || '').trim()
  const aiReportStatus = analysis.aiReportStatus || context.aiReportStatus || (ai ? 'READY' : 'PENDING')
  const reportTime = context.reportTime || context.resultTime || '—'
  const requestId = context.checkRequestId
  const reportNo = context.reportNo || (requestId != null ? `CHK-${String(requestId).padStart(5, '0')}` : '—')

  return {
    reportType: 'check',
    checkRequestId: requestId,
    reportTitle: itemName,
    reportNo,
    status: context.status,
    itemName,
    header: {
      patientName: context.patientName || '—',
      genderLabel: context.genderLabel || '—',
      ageLabel: context.ageLabel || '—',
      medicalRecordNo: context.medicalRecordNo || '—',
      department: context.department || context.departmentName || '—',
      bodyPart: context.bodyPart || '—',
      examDate: context.examDate || context.executeTime || reportTime,
      purpose: context.purpose || '',
      clinicalDiagnosis: context.clinicalDiagnosis || '—',
      orderRemark: context.orderRemark || context.remark || '',
      modality: context.modality || '',
      itemName,
    },
    findings: {
      findingsText,
      studyStatus: findings.studyStatus || context.studyStatus,
      hasImaging: findings.hasImaging ?? context.hasImaging ?? false,
      ctPreviewUrl: findings.ctPreviewUrl || context.ctPreviewUrl,
      maskPreviewUrl: findings.maskPreviewUrl || context.maskPreviewUrl,
      reportImages: findings.reportImages || context.reportImages || null,
      snapshotMeta: findings.snapshotMeta || context.snapshotMeta || null,
      localSnapshots: findings.localSnapshots || context.localSnapshots || null,
    },
    analysis: {
      aiReportText: ai,
      doctorReportText: doctor,
      aiReportStatus,
    },
    footer: {
      examTime: context.examTime || context.executeTime || reportTime,
      executeTime: context.executeTime || reportTime,
      reportTime,
      orderingDoctorName: context.orderingDoctorName || '—',
      executorName: context.executorName || '—',
      reporterName: context.reporterName || '—',
      reviewerName: context.reviewerName?.trim() || '待审核',
    },
    resultText: composeCheckResultText(findingsText, ai, doctor),
    reportTime,
    findingsText,
    aiReportText: ai,
    doctorReportText: doctor,
    aiReportStatus,
  }
}
