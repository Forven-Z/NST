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
  const templates = {
    '头部 CT': '【AI 智能检查报告】\n颅脑 CT 平扫：脑实质密度未见明显异常；脑室系统大小形态正常；中线结构居中。\nAI 提示：未见明显急性出血或占位征象，请放射科医师结合临床审核。',
    '胸部 CT': '【AI 智能检查报告】\n双肺野清晰，未见明显实变影；纵隔居中；心影大小在正常范围。\nAI 提示：未见明显急性渗出或占位征象，请放射科医师审核。',
  }
  const base = templates[itemName] || templates['头部 CT']
  const findings = (findingsText || '').trim()
  if (!findings) return base
  const snippet = findings.length <= 200 ? findings : `${findings.slice(0, 200)}…`
  return `${base}\n\n【基于检查所见归纳】\n${snippet}`
}

export function composeCheckReportView(context = {}, findings = {}, analysis = {}) {
  const itemName = context.itemName || ''
  const findingsText = (findings.findingsText || context.findingsText || '').trim()
  const ai = (analysis.aiReportText || context.aiReportText || '').trim()
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
      instrumentData: findings.instrumentData || context.instrumentData || '',
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
