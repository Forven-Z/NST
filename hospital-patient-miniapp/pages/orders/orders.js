const { fetchRegisterOrders } = require('../../api/orders')
const { formatDisplayDate } = require('../../utils/date')
const billUtil = require('../../utils/bill-util')

var ACTION_LABELS = {
  pay: '去缴费',
  report: '查看报告',
  prescription: '查看处方',
}

Page({
  data: {
    registerId: null,
    registerSummary: null,
    list: [],
    loading: false,
    loadError: false,
  },

  onLoad(options) {
    this.setData({ registerId: options.registerId })
  },

  onShow() {
    if (this.data.registerId) this.load()
  },

  onPullDownRefresh() {
    this.load().finally(function () {
      wx.stopPullDownRefresh()
    })
  },

  load() {
    var that = this
    this.setData({ loading: true, loadError: false })
    return fetchRegisterOrders(this.data.registerId).then(function (res) {
      var data = (res && res.data) || {}
      var list = (data.list || []).map(function (item) {
        var action = item.action || 'none'
        return Object.assign({}, item, {
          actionLabel: ACTION_LABELS[action] || '',
        })
      })
      var summary = data.registerSummary || null
      if (summary && summary.workDate) {
        summary = Object.assign({}, summary, {
          workDateLabel: formatDisplayDate(summary.workDate),
        })
      }
      that.setData({
        list: list,
        registerSummary: summary,
        loading: false,
        loadError: false,
      })
      if (summary) {
        wx.setNavigationBarTitle({
          title: (summary.deptName || '医嘱') + ' · 进度',
        })
      }
    }).catch(function () {
      that.setData({ loading: false, loadError: true, list: [] })
    })
  },

  onRetry() {
    this.load()
  },

  onOrderAction(e) {
    var item = e.currentTarget.dataset.item
    if (!item) return
    var registerId = this.data.registerId
    if (item.action === 'pay') {
      wx.navigateTo({
        url: billUtil.buildBillsUrl({ tab: 'pending', registerId: registerId }),
      })
      return
    }
    if (item.action === 'prescription') {
      this.goPrescription({ currentTarget: { dataset: { item: item } } })
      return
    }
    if (item.action === 'report') {
      this.goReport({ currentTarget: { dataset: { item: item } } })
    }
  },

  goReport(e) {
    var item = e.currentTarget.dataset.item
    if (!item || item.status < 40) {
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
  },

  goPrescription(e) {
    var item = e.currentTarget.dataset.item
    if (!item || item.kind !== 'prescription') return
    wx.navigateTo({
      url: '/pages/prescription/detail/detail?prescriptionId=' + item.requestId,
    })
  },
})
