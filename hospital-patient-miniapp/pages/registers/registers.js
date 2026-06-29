const { fetchMyRegisters, cancelRegister, fetchPendingBills } = require('../../api/patient')
const { visitStateLabel, canCancel, canViewQueue } = require('../../utils/visit-state')
const { isLoggedIn, requireLogin } = require('../../utils/auth')
const patientContext = require('../../utils/patient-context')
const billUtil = require('../../utils/bill-util')
const { visitHubUrl } = require('../../api/visits')
const { formatDisplayDate } = require('../../utils/date')

var PAGE_SIZE = 20

Page({
  data: {
    loggedIn: false,
    loading: false,
    loadError: false,
    list: [],
    filter: 'all',
    page: 1,
    hasMore: true,
    loadingMore: false,
  },

  onLoad(options) {
    if (options.highlightRegisterId) {
      this._highlightRegisterId = Number(options.highlightRegisterId)
    }
  },

  onShow() {
    var loggedIn = isLoggedIn()
    this.setData({ loggedIn: loggedIn })
    if (!loggedIn) {
      this.setData({ list: [], loading: false })
      return
    }
    this.setData({ page: 1, hasMore: true, list: [] })
    this.loadList(true)
  },

  onPullDownRefresh() {
    this.setData({ page: 1, hasMore: true })
    this.loadList(true).finally(function () { wx.stopPullDownRefresh() })
  },

  onReachBottom() {
    if (!this.data.hasMore || this.data.loadingMore || this.data.loading) return
    this.loadList(false)
  },

  onGoLogin() {
    wx.navigateTo({
      url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/registers/registers'),
    })
  },

  onFilter(e) {
    this.setData({ filter: e.currentTarget.dataset.filter, page: 1, hasMore: true, list: [] })
    this.loadList(true)
  },

  onRetry() {
    this.setData({ page: 1, hasMore: true })
    this.loadList(true)
  },

  loadList(reset) {
    var that = this
    var page = reset ? 1 : this.data.page + 1
    if (reset) {
      this.setData({ loading: true, loadError: false })
    } else {
      this.setData({ loadingMore: true })
    }
    var params = { page: page, pageSize: PAGE_SIZE }
    if (this.data.filter !== 'all') {
      params.visitState = Number(this.data.filter)
    }
    var active = patientContext.getActiveMember()
    if (active.memberPatientId) {
      params.patientId = active.memberPatientId
    }
    return fetchMyRegisters(params).then(function (res) {
      var raw = (res.data && res.data.list) || []
      var highlightId = that._highlightRegisterId
      return Promise.all(raw.map(function (r) {
        var row = Object.assign({}, r, {
          cancellable: r.cancellable,
          cancelHint: r.cancelHint,
          stateLabel: visitStateLabel(r.visitState),
          canCancel: canCancel(r),
          canQueue: canViewQueue(r.visitState),
          hasMedicalRecord: !!r.hasMedicalRecord || r.medicalRecordStatus === 2,
          workDateLabel: formatDisplayDate(r.workDate),
          showOrders: r.visitState >= 1 && r.visitState !== 4,
          highlighted: highlightId && r.registerId === highlightId,
          showVisitPay: false,
        })
        if (r.visitState !== 3) return row
        return fetchPendingBills({ status: 0, registerId: r.registerId }).then(function (billRes) {
          var pending = (billRes && billRes.data && billRes.data.list) || []
          row.showVisitPay = pending.length > 0
          return row
        }).catch(function () { return row })
      })).then(function (mapped) {
        var list = reset ? mapped : that.data.list.concat(mapped)
        that.setData({
          list: list,
          page: page,
          hasMore: raw.length >= PAGE_SIZE,
          loading: false,
          loadingMore: false,
          loadError: false,
        })
      })
    }).catch(function () {
      that.setData({ loading: false, loadingMore: false, loadError: reset })
    })
  },

  goPay(e) {
    var id = e && e.currentTarget && e.currentTarget.dataset.id
    if (!id) {
      wx.showToast({ title: '挂号单号缺失', icon: 'none' })
      return
    }
    wx.navigateTo({
      url: billUtil.buildBillsUrl({
        tab: 'pending',
        scope: 'outpatient',
        registerId: id,
      }),
    })
  },

  goPayVisit(e) {
    var id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: billUtil.buildBillsUrl({ tab: 'pending', registerId: id }),
    })
  },

  goQueue(e) {
    var id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/queue/queue?registerId=' + id })
  },

  goVisitHub(e) {
    var id = e && e.currentTarget && e.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({ url: visitHubUrl(id) })
  },

  onCancel(e) {
    var that = this
    var id = e.currentTarget.dataset.id
    wx.showModal({
      title: '退号',
      content: '确定取消该挂号？已支付将按规则原路退回',
      success: function (res) {
        if (!res.confirm) return
        cancelRegister(id, '患者主动退号').then(function () {
          wx.showToast({ title: '退号成功', icon: 'success' })
          that.setData({ page: 1, hasMore: true })
          that.loadList(true)
        }).catch(function (err) {
          wx.showToast({ title: (err && err.message) || '退号失败', icon: 'none' })
        })
      },
    })
  },

  onCardTap(e) {
    var item = e.currentTarget.dataset.item
    if (!item) return

    // 接诊中 / 看诊结束：点卡片进入就诊记录 Hub
    if (item.visitState === 2 || item.visitState === 3) {
      this.goVisitHub({ currentTarget: { dataset: { id: item.registerId } } })
      return
    }

    var actions = []
    var handlers = []

    if (item.visitState === 0) {
      actions.push('去支付')
      handlers.push(function () { this.goPay({ currentTarget: { dataset: { id: item.registerId } } }) }.bind(this))
    }
    if (item.showVisitPay) {
      actions.push('去缴费')
      handlers.push(function () { this.goPayVisit({ currentTarget: { dataset: { id: item.registerId } } }) }.bind(this))
    }
    if (item.canQueue) {
      actions.push('候诊进度')
      handlers.push(function () { this.goQueue({ currentTarget: { dataset: { id: item.registerId } } }) }.bind(this))
    }
    if (item.canCancel) {
      actions.push('退号')
      handlers.push(function () { this.onCancel({ currentTarget: { dataset: { id: item.registerId } } }) }.bind(this))
    }

    if (!actions.length) {
      wx.showToast({ title: item.stateLabel || '暂无可用操作', icon: 'none' })
      return
    }

    wx.showActionSheet({
      itemList: actions,
      success: function (res) {
        var fn = handlers[res.tapIndex]
        if (fn) fn()
      },
    })
  },
})
