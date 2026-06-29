var billUtil = require('./bill-util')

var ACTION_LABELS = {
  pay: '去缴费',
  report: '查看报告',
  prescription: '查看处方',
}

function mapOrderList(list) {
  return (list || []).map(function (item) {
    var action = item.action || 'none'
    return Object.assign({}, item, {
      actionLabel: ACTION_LABELS[action] || '',
    })
  })
}

function handleOrderAction(item, registerId) {
  if (!item) return
  if (item.action === 'pay') {
    wx.navigateTo({
      url: billUtil.buildBillsUrl({ tab: 'pending', registerId: registerId }),
    })
    return
  }
  if (item.action === 'prescription') {
    wx.navigateTo({
      url: '/pages/prescription/detail/detail?prescriptionId=' + item.requestId,
    })
    return
  }
  if (item.action === 'report') {
    if (item.status < 40) {
      wx.showToast({ title: '结果尚未出具', icon: 'none' })
      return
    }
    if (item.kind === 'prescription') {
      wx.navigateTo({
        url: '/pages/prescription/detail/detail?prescriptionId=' + item.requestId,
      })
      return
    }
    var type = item.kind === 'inspection' ? 'lab' : item.kind === 'check' ? 'exam' : 'disposal'
    wx.navigateTo({
      url: '/pages/reports/detail/detail?type=' + type + '&requestId=' + item.requestId,
    })
  }
}

module.exports = {
  ACTION_LABELS,
  mapOrderList,
  handleOrderAction,
}
