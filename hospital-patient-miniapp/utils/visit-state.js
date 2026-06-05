/** 就诊状态文案 */
const VISIT_STATE = {
  0: { label: '待支付', color: '#64748b' },
  1: { label: '已挂号', color: '#d97706' },
  2: { label: '接诊中', color: '#059669' },
  3: { label: '看诊结束', color: '#64748b' },
  4: { label: '已退号', color: '#94a3b8' },
}

const RELATION_TYPES = [
  { value: 1, label: '父母' },
  { value: 2, label: '配偶' },
  { value: 3, label: '子女' },
  { value: 4, label: '其他' },
]

function visitStateLabel(state) {
  var item = VISIT_STATE[state]
  return (item && item.label) || String(state)
}

function canCancel(state) {
  return state === 1
}

function canPayQueue(state) {
  return state === 0
}

function canViewQueue(state) {
  return state === 1 || state === 2
}

module.exports = {
  VISIT_STATE,
  RELATION_TYPES,
  visitStateLabel,
  canCancel,
  canPayQueue,
  canViewQueue,
}
