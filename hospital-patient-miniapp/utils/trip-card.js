var visitState = require('./visit-state')
var billUtil = require('./bill-util')

/**
 * 根据挂号单与待缴情况生成行程卡展示（P1，无检查进度）
 */
function buildTripCard(reg, pendingBills) {
  if (!reg) return null

  var state = reg.visitState
  var card = {
    registerId: reg.registerId,
    deptName: reg.deptName,
    doctorName: reg.doctorName,
    levelName: reg.registLevelName || reg.levelName || '',
    workDate: reg.workDate,
    noonLabel: reg.noonLabel || '',
    visitState: state,
    stateLabel: visitState.visitStateLabel(state),
    subHint: '',
    actionText: '查看详情',
    actionType: 'registers',
    actionUrl: '/pages/registers/registers',
  }

  if (state === 0) {
    card.actionText = '去支付'
    card.actionType = 'bills'
    card.actionUrl = billUtil.buildBillsUrl({
      tab: 'pending',
      scope: 'outpatient',
      registerId: reg.registerId,
    })
    return card
  }

  if (state === 1) {
    card.actionText = '排队候诊'
    card.actionType = 'queue'
    card.actionUrl = '/pages/queue/queue?registerId=' + reg.registerId
    return card
  }

  if (state === 2) {
    card.actionText = '查看详情'
    card.actionType = 'registers'
    card.subHint = '医生正在接诊'
    return card
  }

  if (state === 3) {
    var examPending = (pendingBills || []).filter(function (b) {
      return billUtil.matchesScope(b, 'exam')
    })
    if (examPending.length > 0) {
      card.subHint = '医生已开检查/检验，共 ' + examPending.length + ' 笔待缴'
      card.actionText = '去缴费'
      card.actionType = 'bills'
      card.actionUrl = billUtil.buildBillsUrl({
        tab: 'pending',
        scope: 'exam',
        registerId: reg.registerId,
      })
    } else {
      card.subHint = '本次就医已结束'
      card.actionText = '查报告'
      card.actionType = 'reports'
      card.actionUrl = '/pages/reports/reports'
    }
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
}
