/** 病历文书区块字段（患者 Hub / 详情共用） */
var FIELD_SECTIONS = [
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

function buildRecordSections(record) {
  if (!record) return []
  return FIELD_SECTIONS.filter(function (item) {
    var val = record[item.key]
    return val && String(val).trim()
  }).map(function (item) {
    return { label: item.label, value: record[item.key] }
  })
}

function buildDiseaseNames(record) {
  if (!record || !record.diseaseEntries || !record.diseaseEntries.length) return ''
  return record.diseaseEntries.map(function (d) {
    return d.diseaseName || d.disease_name || ''
  }).filter(Boolean).join('、')
}

module.exports = {
  FIELD_SECTIONS,
  buildRecordSections,
  buildDiseaseNames,
}
