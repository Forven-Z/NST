const { fetchVisits } = require('../../api/visits')
const { visitHubUrl } = require('../../api/visits')
const { isLoggedIn, requireLogin } = require('../../utils/auth')
const { showMemberSwitchSheet } = require('../../utils/member-switch')
const patientContext = require('../../utils/patient-context')

Page({
  data: {
    loggedIn: false,
    list: [],
    loading: false,
    activeMemberName: '本人',
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
      activeMemberName: active.realName || '就诊人',
    })
    this.loadList()
  },

  onPullDownRefresh() {
    this.loadList().finally(function () {
      wx.stopPullDownRefresh()
    })
  },

  onGoLogin() {
    wx.navigateTo({
      url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/medical-record/medical-record'),
    })
  },

  loadList() {
    var that = this
    var active = patientContext.getActiveMember()
    var params = { page: 1, pageSize: 50 }
    if (active.memberPatientId) {
      params.patientId = active.memberPatientId
    }
    this.setData({ loading: true })
    return fetchVisits(params).then(function (res) {
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
    if (!requireLogin({ mode: 'modal', message: '查看就诊记录请先登录' })) return
    var item = e.currentTarget.dataset.item
    if (!item || !item.registerId) return
    wx.navigateTo({ url: visitHubUrl(item.registerId) })
  },

  onSwitchMember() {
    var that = this
    showMemberSwitchSheet({
      onSwitched: function () {
        var active = patientContext.getActiveMember()
        that.setData({ activeMemberName: active.realName || '就诊人' })
        that.loadList()
      },
    })
  },
})
