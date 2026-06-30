/** 病历文书区块（患者 Hub / 医生只读 Hub 共用） */
export const FIELD_SECTIONS = [
  { key: 'readme', label: '主诉' },
  { key: 'present', label: '现病史' },
  { key: 'presentTreat', label: '现病治疗情况' },
  { key: 'history', label: '既往史' },
  { key: 'allergy', label: '过敏史' },
  { key: 'physique', label: '体格检查' },
  { key: 'diagnosis', label: '诊断' },
  { key: 'cure', label: '处理 / 治疗意见' },
  { key: 'checkAdvice', label: '检查建议' },
  { key: 'inspectionAdvice', label: '检验建议' },
]

export function buildRecordSections(record) {
  if (!record) return []
  return FIELD_SECTIONS.filter((item) => {
    const val = record[item.key]
    return val && String(val).trim()
  }).map((item) => ({ label: item.label, value: record[item.key] }))
}

export function buildDiseaseNames(record) {
  if (!record?.diseaseEntries?.length) return ''
  return record.diseaseEntries
    .map((d) => d.diseaseName || d.disease_name || '')
    .filter(Boolean)
    .join('、')
}

export function medicalRecordStatusLabel(status) {
  if (status === 2) return '已确诊提交'
  if (status === 1) return '已保存未提交'
  if (status === 0) return '书写中'
  return '—'
}
