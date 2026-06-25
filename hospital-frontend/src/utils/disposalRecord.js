/**
 * 处置记录视图拼接（与后端 DisposalRecordComposer 字段对齐）
 */

const PROCESS_PLACEHOLDERS = {
  洗胃: '例：左侧卧位，16Fr 胃管；温盐水 500ml 入/450ml 出；生命体征平稳…',
  静脉输液: '例：左前臂留置针，生理盐水 500ml 静滴，滴速 40 滴/分，无渗漏…',
  雾化吸入: '例：布地奈德+沙丁胺醇雾化 15 分钟，SpO₂ 98%，无呛咳…',
  导尿: '例：常规消毒，14Fr 导尿管顺利置入，引出尿色清，约 300ml…',
}

export function processPlaceholder(itemName) {
  return PROCESS_PLACEHOLDERS[itemName] || '描述处置步骤、用量、操作方式及患者即时反应…'
}

export function composeDisposalResultText(processText = '', outcomeText = '') {
  const process = (processText || '').trim()
  const outcome = (outcomeText || '').trim()
  if (!process && !outcome) return ''
  const parts = []
  if (process) parts.push('【处置过程】', process)
  if (outcome) parts.push('', '【观察与结果】', outcome)
  return parts.join('\n').trim()
}

export function parseDisposalResultText(resultText) {
  const text = (resultText || '').trim()
  if (!text) return { processText: '', outcomeText: '' }
  const match = /^【处置过程】([\s\S]*?)(?:\n【观察与结果】([\s\S]*))?$/.exec(text)
  if (match) {
    return {
      processText: (match[1] || '').trim(),
      outcomeText: (match[2] || '').trim(),
    }
  }
  const legacy = /^AI：([\s\S]*?)(?:\n医师：([\s\S]*))?$/.exec(text)
  if (legacy) {
    return { processText: '', outcomeText: (legacy[2] || legacy[1] || '').trim() }
  }
  return { processText: '', outcomeText: text }
}

export function composeDisposalRecordView(context = {}, record = {}) {
  const itemName = context.itemName || ''
  const processText = (record.processText || '').trim()
  const outcomeText = (record.outcomeText || '').trim()
  const reportTime = context.reportTime || context.resultTime || '—'
  const requestId = context.disposalRequestId
  const recordNo =
    context.recordNo ||
    (requestId != null ? `DIS-${String(requestId).padStart(5, '0')}` : '—')

  return {
    reportType: 'disposal',
    disposalRequestId: requestId,
    reportTitle: itemName,
    recordNo,
    status: context.status,
    itemName,
    header: {
      patientName: context.patientName || '—',
      genderLabel: context.genderLabel || '—',
      ageLabel: context.ageLabel || '—',
      medicalRecordNo: context.medicalRecordNo || '—',
      department: context.department || context.departmentName || '—',
      clinicalDiagnosis: context.clinicalDiagnosis || '—',
      itemName,
      purpose: context.purpose || '',
      bodyPart: context.bodyPart || '',
      orderRemark: context.orderRemark || context.remark || '',
    },
    record: {
      processText,
      outcomeText,
    },
    footer: {
      executeTime: context.executeTime || reportTime,
      recordTime: reportTime,
      orderingDoctorName: context.orderingDoctorName || '—',
      executorName: context.executorName || '—',
      recorderName: context.recorderName || '—',
      reviewerName: context.reviewerName?.trim() || '待审核',
    },
    resultText: composeDisposalResultText(processText, outcomeText),
    reportTime,
  }
}
