/**
 * 检验报告视图拼接（与后端 LabReportComposer 字段对齐，Mock 与三端展示共用）
 */

const SAMPLE_TYPE = {
  血常规: '全血',
  C反应蛋白: '全血',
  降钙素原: '全血',
  尿常规: '尿液',
  粪便常规及隐血: '粪便',
  空腹血糖: '血清',
  血脂四项: '血清',
}

const ITEM_TEMPLATES = {
  血常规: [
    { code: 'WBC', name: '白细胞', result: '12.8', unit: '×10⁹/L', refRange: '3.5-9.5', flag: 'H' },
    { code: 'RBC', name: '红细胞', result: '4.65', unit: '×10¹²/L', refRange: '4.3-5.8', flag: 'N' },
    { code: 'HGB', name: '血红蛋白', result: '138', unit: 'g/L', refRange: '130-175', flag: 'N' },
    { code: 'PLT', name: '血小板', result: '210', unit: '×10⁹/L', refRange: '125-350', flag: 'N' },
    { code: 'NEUT%', name: '中性粒细胞%', result: '62.0', unit: '%', refRange: '40-75', flag: 'N' },
    { code: 'LYMPH%', name: '淋巴细胞%', result: '28.5', unit: '%', refRange: '20-50', flag: 'N' },
  ],
  C反应蛋白: [
    { code: 'CRP', name: 'C反应蛋白', result: '18.6', unit: 'mg/L', refRange: '0-8', flag: 'H' },
  ],
  血脂四项: [
    { code: 'TC', name: '总胆固醇', result: '5.82', unit: 'mmol/L', refRange: '3.1-5.7', flag: 'H' },
    { code: 'TG', name: '甘油三酯', result: '2.15', unit: 'mmol/L', refRange: '0.45-1.7', flag: 'H' },
    { code: 'HDL-C', name: '高密度脂蛋白', result: '1.05', unit: 'mmol/L', refRange: '1.0-1.6', flag: 'N' },
    { code: 'LDL-C', name: '低密度脂蛋白', result: '3.68', unit: 'mmol/L', refRange: '0-3.4', flag: 'H' },
  ],
  空腹血糖: [
    { code: 'GLU', name: '空腹血糖', result: '6.8', unit: 'mmol/L', refRange: '3.9-6.1', flag: 'H' },
  ],
  尿常规: [
    { code: 'PRO', name: '尿蛋白', result: '阴性', unit: '', refRange: '阴性', flag: 'N' },
    { code: 'GLU-U', name: '尿糖', result: '阴性', unit: '', refRange: '阴性', flag: 'N' },
    { code: 'WBC-U', name: '尿白细胞', result: '0-3', unit: '/HP', refRange: '0-5', flag: 'N' },
  ],
}

export function defaultLabItems(itemName) {
  const list = ITEM_TEMPLATES[itemName]
  if (list) return list.map((item, i) => ({ ...item, sortOrder: i }))
  return [
    { sortOrder: 0, code: 'ITEM1', name: '检验项目A', result: '—', unit: '', refRange: '—', flag: '' },
    { sortOrder: 1, code: 'ITEM2', name: '检验项目B', result: '—', unit: '', refRange: '—', flag: '' },
  ]
}

export function sampleTypeFor(itemName) {
  return SAMPLE_TYPE[itemName] || '标本'
}

function formatItemLine(item) {
  const arrow = item.flag === 'H' ? ' ↑' : item.flag === 'L' ? ' ↓' : ''
  const unit = item.unit ? ` ${item.unit}` : ''
  const ref = item.refRange ? `  参考 ${item.refRange}` : ''
  return `${item.name}  ${item.result}${unit}${ref}${arrow}`
}

export function composeLabResultText(items, aiReportText = '', doctorReportText = '') {
  const parts = []
  if (items?.length) {
    parts.push('【检验结果】', ...items.map(formatItemLine))
  }
  const ai = (aiReportText || '').trim()
  const doctor = (doctorReportText || '').trim()
  if (ai) parts.push('', '【诊断分析】', ai)
  if (doctor) parts.push('', '【医师意见】', doctor)
  return parts.join('\n').trim()
}

/** 从 result_text 还原 AI 分析与医师意见（与 LabReportComposer.parsePublishedText 对齐） */
export function parseLabPublishedText(resultText) {
  const text = (resultText || '').trim()
  if (!text) return { aiReportText: '', doctorReportText: '' }

  if (text.includes('【诊断分析】') || text.includes('【医师意见】')) {
    const extract = (startMarker, endMarker) => {
      const start = text.indexOf(startMarker)
      if (start < 0) return ''
      let from = start + startMarker.length
      while (from < text.length && (text[from] === '\n' || text[from] === '\r')) from += 1
      if (!endMarker) return text.slice(from).trim()
      const end = text.indexOf(endMarker, from)
      return (end < 0 ? text.slice(from) : text.slice(from, end)).trim()
    }
    return {
      aiReportText: extract('【诊断分析】', '【医师意见】'),
      doctorReportText: extract('【医师意见】', null),
    }
  }

  const match = /^AI：([\s\S]*?)(?:\n医师：([\s\S]*))?$/.exec(text)
  if (match) {
    return { aiReportText: (match[1] || '').trim(), doctorReportText: (match[2] || '').trim() }
  }
  return { aiReportText: text, doctorReportText: '' }
}

export function generateLabAiReportStub(itemName, items = []) {
  const abnormal = (items || [])
    .filter((i) => i.flag === 'H' || i.flag === 'L')
    .map((i) => `${i.name} ${i.result}${i.unit || ''}${i.flag === 'H' ? '↑' : '↓'}`)
  let text = `【AI 智能检验报告 · ${itemName || '检验'}】\n`
  if (!abnormal.length) {
    text += '综合检验指标：各项目均在参考范围内或未见明显异常模式。\n'
    text += 'AI 提示：建议结合临床症状继续观察，请检验师审核确认。'
  } else {
    text += `异常项目：${abnormal.join('；')}。\n`
    text += 'AI 提示：存在偏离参考范围指标，建议结合临床排查感染、代谢或炎症可能，请检验师审核确认。'
  }
  return text
}

export function composeLabReportView(context = {}, items = [], analysis = {}) {
  const itemName = context.itemName || ''
  const safeItems = items?.length ? items : defaultLabItems(itemName)
  const ai = (analysis.aiReportText || '').trim()
  const doctor = (analysis.doctorReportText || '').trim()
  const aiReportStatus = analysis.aiReportStatus || (ai ? 'READY' : 'PENDING')
  const reportTime = context.reportTime || context.resultTime || '—'

  return {
    reportType: 'lab',
    inspectionRequestId: context.inspectionRequestId,
    reportTitle: itemName,
    reportNo: context.reportNo || `LAB-${String(context.inspectionRequestId || 0).padStart(5, '0')}`,
    status: context.status,
    itemName,
    header: {
      patientName: context.patientName || '—',
      genderLabel: context.genderLabel || '—',
      ageLabel: context.ageLabel || '—',
      medicalRecordNo: context.medicalRecordNo || '—',
      sampleType: sampleTypeFor(itemName),
      sourceLabel: '门诊',
      department: context.department || context.departmentName || '—',
      clinicalDiagnosis: context.clinicalDiagnosis || '—',
      purpose: context.purpose || '',
      bodyPart: context.bodyPart || '',
      remark: context.orderRemark || context.remark || '',
    },
    items: safeItems,
    analysis: {
      aiReportText: ai,
      doctorReportText: doctor,
      aiReportStatus,
    },
    footer: {
      executeTime: context.executeTime || reportTime,
      reportTime,
      orderingDoctorName: context.orderingDoctorName || '—',
      testerName: context.testerName || '—',
      reporterName: context.reporterName || context.testerName || '—',
      reviewerName: context.reviewerName?.trim() || '待审核',
    },
    resultText: composeLabResultText(safeItems, ai, doctor),
    aiReportText: ai,
    doctorReportText: doctor,
    aiReportStatus,
    reportTime,
  }
}

export function flagLabel(flag) {
  if (flag === 'H') return '↑'
  if (flag === 'L') return '↓'
  return ''
}

export function flagClass(flag) {
  if (flag === 'H' || flag === 'L') return 'abnormal'
  return ''
}
