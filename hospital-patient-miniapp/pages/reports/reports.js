const { fetchReports } = require('../../api/patient')
const { getAccessToken } = require('../../utils/auth')
const patientContext = require('../../utils/patient-context')

Page({
  data: {
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
    if (!getAccessToken()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    var active = patientContext.getActiveMember()
    this.setData({
      activeMemberName: active.realName + (active.isSelf ? '（本人）' : ''),
      visitPatientId: active.memberPatientId,
    })
    this.load()
  },

  onTab(e) {
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
