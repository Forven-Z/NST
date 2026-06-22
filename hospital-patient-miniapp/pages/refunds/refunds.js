const { fetchRefunds } = require('../../api/patient')
const patientContext = require('../../utils/patient-context')

Page({
  data: {
    loading: false,
    refunds: [],
  },

  onShow() {
    this.loadRefunds()
  },

  onPullDownRefresh() {
    this.loadRefunds().then(function () {
      wx.stopPullDownRefresh()
    }).catch(function () {
      wx.stopPullDownRefresh()
    })
  },

  loadRefunds() {
    var that = this
    var active = patientContext.getActiveMember()
    this.setData({ loading: true })
    return fetchRefunds({ patientId: active.memberPatientId }).then(function (res) {
      var list = (res && res.data && res.data.list) || []
      that.setData({ refunds: list, loading: false })
    }).catch(function (err) {
      that.setData({ loading: false })
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    })
  },
})
