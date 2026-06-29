const { fetchRefunds } = require('../../api/patient')
const patientContext = require('../../utils/patient-context')

Page({
  data: {
    loading: false,
    loadError: false,
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
    this.setData({ loading: true, loadError: false })
    return fetchRefunds({ patientId: active.memberPatientId }).then(function (res) {
      var list = (res && res.data && res.data.list) || []
      that.setData({ refunds: list, loading: false, loadError: false })
    }).catch(function () {
      that.setData({ loading: false, loadError: true })
    })
  },

  onRetry() {
    this.loadRefunds()
  },

  onDetail(e) {
    var item = e.currentTarget.dataset.item
    if (!item) return
    wx.navigateTo({
      url: '/pages/refunds/detail/detail?payload=' + encodeURIComponent(JSON.stringify(item)),
    })
  },
})
