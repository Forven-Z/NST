var visitState = require('./visit-state')
var billUtil = require('./bill-util')
var dateUtil = require('./date')

function visitHubPath(registerId) {
  return '/pages/medical-record/detail/detail?registerId=' + registerId
}

function buildPendingSubHint(pendingBills) {
  var bills = pendingBills || []
  if (!bills.length) return ''
  var summary = billUtil.summarizePendingBills(bills)
  var parts = ['共 ' + bills.length + ' 笔待缴']
  if (summary.examCount > 0 && summary.pharmacyCount > 0) {
    parts.push('（医技 ' + summary.examCount + ' 笔、药费 ' + summary.pharmacyCount + ' 笔）')
  } else if (summary.examCount > 0) {
    parts.push('（医技 ' + summary.examCount + ' 笔）')
  } else if (summary.pharmacyCount > 0) {
    parts.push('（药费 ' + summary.pharmacyCount + ' 笔）')
  }
  return parts.join('')
}

function hasInProgressOrders(ordersList) {
  return (ordersList || []).some(function (item) {
    return item.status === 20 || item.status === 30
  })
}

function buildBillsUrlForRegister(registerId, scope) {
  var opts = { tab: 'pending', registerId: registerId }
  if (scope) opts.scope = scope
  return billUtil.buildBillsUrl(opts)
}

/**
 * 根据挂号单、待缴与医嘱生成行程卡（同一账户多挂号时由 pickActiveRegister 取最新一条）
 */
function buildTripCard(reg, pendingBills, ordersList) {
  if (!reg) return null

  var state = reg.visitState
  var registerId = reg.registerId
  var card = {
    registerId: registerId,
    deptName: reg.deptName,
    doctorName: reg.doctorName,
    levelName: reg.registLevelName || reg.levelName || '',
    workDate: dateUtil.formatDisplayDate(reg.workDate),
    noonLabel: reg.noonLabel || '',
    visitState: state,
    stateLabel: visitState.visitStateLabel(state),
    subHint: '',
    actionText: '查看详情',
    actionType: 'registers',
    actionUrl: '/pages/registers/registers',
  }

  if (state === 0) {
    card.subHint = '挂号费待支付'
    card.actionText = '去支付'
    card.actionType = 'bills'
    card.actionUrl = buildBillsUrlForRegister(registerId, 'outpatient')
    return card
  }

  if (state === 1) {
    card.subHint = '请提前到科室候诊'
    card.actionText = '候诊进度'
    card.actionType = 'queue'
    card.actionUrl = '/pages/queue/queue?registerId=' + registerId
    return card
  }

  if (state === 2) {
    card.subHint = '医生正在接诊'
    card.actionText = '查看本次就诊'
    card.actionType = 'visit'
    card.actionUrl = visitHubPath(registerId)
    return card
  }

  if (state === 3) {
    var pending = pendingBills || []
    if (pending.length > 0) {
      card.subHint = buildPendingSubHint(pending)
      card.actionText = '去缴费'
      card.actionType = 'bills'
      card.actionUrl = buildBillsUrlForRegister(registerId)
      return card
    }
    if (hasInProgressOrders(ordersList)) {
      card.subHint = '检查/检验进行中，报告尚未出具'
      card.actionText = '查看本次就诊'
      card.actionType = 'visit'
      card.actionUrl = visitHubPath(registerId)
      return card
    }
    card.subHint = '本次就医已结束'
    card.actionText = '查看本次就诊'
    card.actionType = 'visit'
    card.actionUrl = visitHubPath(registerId)
    return card
  }

  return card
}

function pickActiveRegister(list) {
  var active = (list || []).filter(function (r) {
    return r.visitState !== 4
  })
  active.sort(function (a, b) {
    return (b.registerId || 0) - (a.registerId || 0)
  })
  return active[0] || null
}

module.exports = {
  buildTripCard,
  pickActiveRegister,
  hasInProgressOrders,
}
