const { fetchBills, fetchPayments } = require('../../api/patient')
const billUtil = require('../../utils/bill-util')

const patientContext = require('../../utils/patient-context')

const { isLoggedIn, requireLogin } = require('../../utils/auth')

const { runSimulatedWechatPay } = require('../../utils/simulated-wechat-pay')



Page({

  data: {

    loggedIn: false,

    pageTab: 'pending',

    scope: 'all',

    registerId: '',

    loading: false,

    loadError: false,

    paying: false,

    bills: [],

    payments: [],

    selectedIds: [],

    totalAmount: 0,

    scopeTitle: '待缴费用',

    registerHint: '',

  },



  onLoad(options) {

    var pageTab = options.tab === 'paid' ? 'paid' : 'pending'

    var scope = options.scope || 'all'

    var registerId = options.registerId || ''

    var scopeTitle = '待缴费用'

    if (scope === 'outpatient') scopeTitle = '门诊待缴'

    if (scope === 'exam') scopeTitle = '医技待缴'

    if (pageTab === 'paid') scopeTitle = '缴费记录'

    if (registerId && pageTab === 'pending') scopeTitle = '本次就诊待缴'

    this.setData({

      pageTab: pageTab,

      scope: scope,

      registerId: registerId,

      scopeTitle: scopeTitle,

      registerHint: registerId ? '仅显示挂号单 #' + registerId + ' 相关费用' : '',

    })

    wx.setNavigationBarTitle({

      title: pageTab === 'paid' ? '缴费记录' : (registerId ? '本次就诊待缴' : '我的费用'),

    })

  },



  onShow() {

    var loggedIn = isLoggedIn()

    this.setData({ loggedIn: loggedIn })

    if (!loggedIn) {

      this.setData({ bills: [], payments: [], loading: false })

      return

    }

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



  onGoLogin() {

    var redirect = '/pages/bills/bills'

    if (this.data.pageTab === 'paid') redirect += '?tab=paid'

    if (this.data.registerId) redirect += (redirect.indexOf('?') >= 0 ? '&' : '?') + 'registerId=' + this.data.registerId

    wx.navigateTo({

      url: '/pages/login/login?redirect=' + encodeURIComponent(redirect),

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

    this.setData({ loading: true, loadError: false, selectedIds: [], totalAmount: 0 })

    return fetchBills(params).then(function (res) {

      var list = (res && res.data && res.data.list) || []

      list = list.map(function (b) {

        return {

          id: b.id,

          billTitle: b.billTitle || b.itemName,

          bizType: b.bizType,

          bizTypeLabel: b.bizTypeLabel || billUtil.bizTypeLabel(b.bizType),

          amount: b.amount,

          registerId: b.registerId,

          lineItems: b.lineItems || [],

          checked: false,

          expanded: false,

        }

      })

      that.setData({ bills: list, loading: false, loadError: false })

    }).catch(function () {

      that.setData({ loading: false, loadError: true })

    })

  },



  loadPayments() {

    var that = this

    var active = patientContext.getActiveMember()

    this.setData({ loading: true, loadError: false })

    return fetchPayments({ patientId: active.memberPatientId }).then(function (res) {

      var list = (res && res.data && res.data.list) || []

      that.setData({ payments: list, loading: false, loadError: false })

    }).catch(function () {

      that.setData({ loading: false, loadError: true })

    })

  },



  onRetry() {

    this.loadList()

  },



  goVisitHub() {
    if (!this.data.registerId) return
    var visitHubUrl = require('../../api/visits').visitHubUrl
    wx.navigateTo({ url: visitHubUrl(this.data.registerId) })
  },



  onSwitchPageTab(e) {

    var tab = e.currentTarget.dataset.tab

    if (tab === this.data.pageTab) return

    var title = tab === 'paid' ? '缴费记录' : (this.data.registerId ? '本次就诊待缴' : '我的费用')

    this.setData({ pageTab: tab })

    wx.setNavigationBarTitle({ title: title })

    this.loadList()

  },



  syncBillSelection(selectedIds) {

    var idSet = {}

    selectedIds.forEach(function (id) { idSet[id] = true })

    var bills = this.data.bills.map(function (b) {

      return Object.assign({}, b, { checked: !!idSet[b.id] })

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



  onToggleExpand(e) {

    var id = Number(e.currentTarget.dataset.id)

    var bills = this.data.bills.map(function (b) {

      if (b.id === id) return Object.assign({}, b, { expanded: !b.expanded })

      return b

    })

    this.setData({ bills: bills })

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

    if (!requireLogin({ mode: 'modal', message: '缴费请先登录' })) return

    if (this.data.paying || this.data.selectedIds.length === 0) {

      wx.showToast({ title: '请选择账单', icon: 'none' })

      return

    }

    this.setData({ paying: true })

    runSimulatedWechatPay({

      billIds: this.data.selectedIds,

      amount: this.data.totalAmount,

      onSuccess: function () {

        return that.loadPending()

      },

      onFail: function () {},

    }).finally(function () {

      that.setData({ paying: false })

    })

  },



  onPaymentDetail(e) {

    if (!requireLogin({ mode: 'modal', message: '查看缴费详情请先登录' })) return

    var id = e.currentTarget.dataset.id

    if (!id) return

    wx.navigateTo({ url: '/pages/bills/detail/detail?paymentId=' + id })

  },

})


