var OUTPATIENT_TYPES = ['REGIST', 'REGISTER']
var EXAM_TYPES = ['CHECK', 'LIS', 'INSPECTION', 'PACS', 'EXAM']

function bizTypeLabel(type) {
  var map = {
    REGIST: '挂号费',
    REGISTER: '挂号费',
    LIS: '检验费',
    INSPECTION: '检验费',
    CHECK: '检查费',
    PACS: '检查费',
    EXAM: '检查费',
    PHARMACY: '药费',
    DRUG: '药费',
  }
  return map[type] || type || '费用'
}

function matchesScope(bill, scope) {
  if (!scope || scope === 'all') return true
  var t = bill.bizType || ''
  if (scope === 'outpatient') {
    return OUTPATIENT_TYPES.indexOf(t) >= 0
  }
  if (scope === 'exam') {
    return EXAM_TYPES.indexOf(t) >= 0
  }
  return true
}

function buildBillsUrl(opts) {
  opts = opts || {}
  var parts = []
  if (opts.tab) parts.push('tab=' + opts.tab)
  if (opts.scope) parts.push('scope=' + opts.scope)
  if (opts.registerId) parts.push('registerId=' + opts.registerId)
  return '/pages/bills/bills' + (parts.length ? '?' + parts.join('&') : '')
}

module.exports = {
  bizTypeLabel,
  matchesScope,
  buildBillsUrl,
  OUTPATIENT_TYPES,
  EXAM_TYPES,
}
