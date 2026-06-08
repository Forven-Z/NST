const { fetchReports } = require('../../api/patient')
const { isLoggedIn, requireLogin } = require('../../utils/auth')
const patientContext = require('../../utils/patient-context')

Page({
  data: {
    loggedIn: false,
    tab: 'all',
    list: [],
    loading: false,
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
    this.setData({ loggedIn: loggedIn })
    if (!loggedIn) {
      this.setData({ list: [], loading: false, activeMemberName: '—' })
      return
    }
    var active = patientContext.getActiveMember()
    this.setData({
      activeMemberName: active.realName + (active.isSelf ? '（本人）' : ''),
      visitPatientId: active.memberPatientId,
    })
    this.load()
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
    this.setData({ loading: true })
    fetchReports(params).then(function (res) {
      that.setData({
        list: (res && res.data && res.data.list) || [],
        loading: false,
      })
    }).catch(function (err) {
      that.setData({ loading: false })
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    })
  },

  onDetail(e) {
    if (!requireLogin({ mode: 'modal', message: '查看报告详情请先登录' })) return
    var item = e.currentTarget.dataset.item
    if (!item) return
    wx.navigateTo({
      url: '/pages/reports/detail/detail?type=' + item.type + '&requestId=' + item.requestId,
    })
  },

  goHomeSwitch() {
    wx.switchTab({ url: '/pages/home/home' })
  },
})
