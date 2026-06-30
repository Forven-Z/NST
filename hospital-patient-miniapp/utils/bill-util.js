var OUTPATIENT_TYPES = ['REGIST', 'REGISTER']
var EXAM_TYPES = ['CHECK', 'LIS', 'INSPECTION', 'PACS', 'EXAM']
var PHARMACY_TYPES = ['PHARMACY', 'DRUG', 'PRESCRIPTION']

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

function isPharmacyBill(bill) {
  var t = (bill && bill.bizType) || ''
  return PHARMACY_TYPES.indexOf(t) >= 0
}

function summarizePendingBills(bills) {
  var examCount = 0
  var pharmacyCount = 0
  ;(bills || []).forEach(function (b) {
    if (matchesScope(b, 'exam')) examCount += 1
    else if (isPharmacyBill(b)) pharmacyCount += 1
  })
  return { examCount: examCount, pharmacyCount: pharmacyCount }
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
  isPharmacyBill,
  summarizePendingBills,
  buildBillsUrl,
  OUTPATIENT_TYPES,
  EXAM_TYPES,
  PHARMACY_TYPES,
}
