const { fetchPaymentDetail } = require('../../../api/patient')
const patientContext = require('../../../utils/patient-context')

Page({
  data: {
    paymentId: '',
    loading: true,
    detail: null,
    bills: [],
  },

  onLoad(options) {
    this.setData({ paymentId: options.paymentId || '' })
  },

  onShow() {
    if (this.data.paymentId) this.loadDetail()
  },

  onPullDownRefresh() {
    this.loadDetail().finally(function () {
      wx.stopPullDownRefresh()
    })
  },

  loadDetail() {
    var that = this
    var active = patientContext.getActiveMember()
    this.setData({ loading: true })
    return fetchPaymentDetail(this.data.paymentId, {
      patientId: active.memberPatientId,
    }).then(function (res) {
      var detail = (res && res.data) || null
      that.setData({
        detail: detail,
        bills: (detail && detail.bills) || [],
        loading: false,
      })
      if (detail && detail.summary) {
        wx.setNavigationBarTitle({ title: '缴费 #' + detail.paymentId })
      }
    }).catch(function (err) {
      that.setData({ loading: false })
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    })
  },
})
