const { fetchReports } = require('../../api/patient')
const { consumePendingReportTab } = require('../../utils/report-nav')
const { groupReportsByDate } = require('../../utils/report-list')
const { showMemberSwitchSheet } = require('../../utils/member-switch')
const { isLoggedIn, requireLogin } = require('../../utils/auth')
const patientContext = require('../../utils/patient-context')

Page({
  data: {
    loggedIn: false,
    tab: 'all',
    list: [],
    groups: [],
    loading: false,
    loadError: false,
    pendingCount: 0,
    emptyHint: '',
    activeMemberName: '本人',
    visitPatientId: null,
  },

  onLoad(options) {
    var tab = 'all'
    if (options.type === 'lab' || options.type === 'exam' || options.type === 'disposal') {
      tab = options.type
    }
    this.setData({ tab: tab })
  },

  onShow() {
    var loggedIn = isLoggedIn()
    var tab = consumePendingReportTab(this.data.tab)
    this.setData({ loggedIn: loggedIn, tab: tab })
    if (!loggedIn) {
      this.setData({
        list: [],
        groups: [],
        loading: false,
        pendingCount: 0,
        emptyHint: '',
        activeMemberName: '—',
      })
      return
    }
    this.syncMember()
    this.load()
  },

  onPullDownRefresh() {
    var that = this
    this.load().finally(function () {
      wx.stopPullDownRefresh()
    })
  },

  syncMember() {
    var active = patientContext.getActiveMember()
    this.setData({
      activeMemberName: active.realName || '就诊人',
      visitPatientId: active.memberPatientId,
    })
  },

  onGoLogin() {
    wx.navigateTo({
      url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/reports/reports'),
    })
  },

  onTab(e) {
    if (!isLoggedIn()) {
      requireLogin({ mode: 'modal', message: '查看报告请先登录' })
      return
    }
    this.setData({ tab: e.currentTarget.dataset.tab })
    this.load()
  },

  load() {
    var that = this
    var active = patientContext.getActiveMember()
    var params = { type: this.data.tab }
    if (active.memberPatientId) {
      params.patientId = active.memberPatientId
    }
    this.setData({ loading: true, loadError: false })
    return fetchReports(params).then(function (res) {
      var data = (res && res.data) || {}
      var list = data.list || []
      var pendingCount = data.pendingCount || 0
      var emptyHint = ''
      if (!list.length && pendingCount > 0) {
        emptyHint = '您有 ' + pendingCount + ' 项检验/检查/处置进行中，结果出具后将显示在此'
      } else if (!list.length) {
        emptyHint = '医生开单并完成检验/检查/处置后，结果将显示在此'
      }
      that.setData({
        list: list,
        groups: groupReportsByDate(list),
        pendingCount: pendingCount,
        emptyHint: emptyHint,
        loading: false,
        loadError: false,
      })
    }).catch(function () {
      that.setData({ loading: false, loadError: true })
    })
  },

  onRetry() {
    this.load()
  },

  onDetail(e) {
    if (!requireLogin({ mode: 'modal', message: '查看报告详情请先登录' })) return
    var item = e.currentTarget.dataset.item
    if (!item) return
    wx.navigateTo({
      url: '/pages/reports/detail/detail?type=' + item.type + '&requestId=' + item.requestId,
    })
  },

  onSwitchMember() {
    var that = this
    showMemberSwitchSheet({
      onSwitched: function () {
        that.syncMember()
        that.load()
      },
    })
  },
})
