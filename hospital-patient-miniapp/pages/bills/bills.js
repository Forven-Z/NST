const { fetchBills, fetchPayments, mockPayment } = require('../../api/patient')
const billUtil = require('../../utils/bill-util')
const patientContext = require('../../utils/patient-context')

Page({
  data: {
    pageTab: 'pending',
    scope: 'all',
    registerId: '',
    loading: false,
    paying: false,
    bills: [],
    payments: [],
    selectedIds: [],
    totalAmount: 0,
    scopeTitle: '全部待缴',
  },

  onLoad(options) {
    var pageTab = options.tab === 'paid' ? 'paid' : 'pending'
    var scope = options.scope || 'all'
    var scopeTitle = '全部待缴'
    if (scope === 'outpatient') scopeTitle = '门诊待缴'
    if (scope === 'exam') scopeTitle = '检查待缴'
    if (pageTab === 'paid') scopeTitle = '缴费记录'
    this.setData({
      pageTab: pageTab,
      scope: scope,
      registerId: options.registerId || '',
      scopeTitle: scopeTitle,
    })
    wx.setNavigationBarTitle({
      title: pageTab === 'paid' ? '缴费记录' : '我的费用',
    })
  },

  onShow() {
    this.loadList()
  },

  onPullDownRefresh() {
    var that = this
    this.loadList().then(function () {
      wx.stopPullDownRefresh()
    }).catch(function () {
      wx.stopPullDownRefresh()
    })
  },

  loadList() {
    if (this.data.pageTab === 'paid') {
      return this.loadPayments()
    }
    return this.loadPending()
  },

  loadPending() {
    var that = this
    var active = patientContext.getActiveMember()
    var params = {
      status: 0,
      scope: this.data.scope,
      patientId: active.memberPatientId,
    }
    if (this.data.registerId) {
      params.registerId = this.data.registerId
    }
    this.setData({ loading: true, selectedIds: [], totalAmount: 0 })
    return fetchBills(params).then(function (res) {
      var list = (res && res.data && res.data.list) || []
      list = list.map(function (b) {
        return {
          id: b.id,
          billTitle: b.billTitle || b.itemName,
          bizType: b.bizType,
          bizTypeLabel: billUtil.bizTypeLabel(b.bizType),
          amount: b.amount,
          registerId: b.registerId,
          checked: false,
        }
      })
      that.setData({ bills: list, loading: false })
    }).catch(function (err) {
      that.setData({ loading: false })
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    })
  },

  loadPayments() {
    var that = this
    var active = patientContext.getActiveMember()
    this.setData({ loading: true })
    return fetchPayments({ patientId: active.memberPatientId }).then(function (res) {
      var list = (res && res.data && res.data.list) || []
      that.setData({ payments: list, loading: false })
    }).catch(function (err) {
      that.setData({ loading: false })
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    })
  },

  onSwitchPageTab(e) {
    var tab = e.currentTarget.dataset.tab
    if (tab === this.data.pageTab) return
    this.setData({ pageTab: tab })
    wx.setNavigationBarTitle({ title: tab === 'paid' ? '缴费记录' : '我的费用' })
    this.loadList()
  },

  syncBillSelection(selectedIds) {
    var idSet = {}
    selectedIds.forEach(function (id) { idSet[id] = true })
    var bills = this.data.bills.map(function (b) {
      return { id: b.id, billTitle: b.billTitle, bizType: b.bizType, bizTypeLabel: b.bizTypeLabel, amount: b.amount, registerId: b.registerId, checked: !!idSet[b.id] }
    })
    var total = 0
    bills.forEach(function (b) {
      if (b.checked) total += Number(b.amount)
    })
    this.setData({
      bills: bills,
      selectedIds: selectedIds,
      totalAmount: Math.round(total * 100) / 100,
    })
  },

  onToggleBill(e) {
    var id = Number(e.currentTarget.dataset.id)
    var selectedIds = this.data.selectedIds.slice()
    var idx = selectedIds.indexOf(id)
    if (idx >= 0) selectedIds.splice(idx, 1)
    else selectedIds.push(id)
    this.syncBillSelection(selectedIds)
  },

  onSelectAll() {
    if (this.data.selectedIds.length === this.data.bills.length) {
      this.syncBillSelection([])
      return
    }
    this.syncBillSelection(this.data.bills.map(function (b) { return b.id }))
  },

  onPay() {
    var that = this
    if (this.data.paying || this.data.selectedIds.length === 0) {
      wx.showToast({ title: '请选择账单', icon: 'none' })
      return
    }
    this.setData({ paying: true })
    mockPayment(this.data.selectedIds).then(function () {
      wx.showToast({ title: '支付成功', icon: 'success' })
      return that.loadPending()
    }).catch(function (err) {
      wx.showToast({ title: (err && err.message) || '支付失败', icon: 'none' })
    }).then(function () {
      that.setData({ paying: false })
    })
  },
})
