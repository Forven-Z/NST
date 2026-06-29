const { fetchPrescription } = require('../../../api/patient')
const billUtil = require('../../../utils/bill-util')

Page({
  data: {
    prescriptionId: '',
    loading: true,
    loadError: false,
    detail: null,
    items: [],
    showPayBar: false,
  },

  onLoad(options) {
    this.setData({ prescriptionId: options.prescriptionId || '' })
  },

  onShow() {
    if (this.data.prescriptionId) this.loadDetail()
  },

  onPullDownRefresh() {
    this.loadDetail().finally(function () {
      wx.stopPullDownRefresh()
    })
  },

  loadDetail() {
    var that = this
    this.setData({ loading: true, loadError: false })
    return fetchPrescription(Number(this.data.prescriptionId)).then(function (res) {
      var detail = (res && res.data) || null
      that.setData({
        detail: detail,
        items: (detail && detail.items) || [],
        showPayBar: !!(detail && detail.status === 10),
        loading: false,
        loadError: false,
      })
    }).catch(function () {
      that.setData({ loading: false, loadError: true, detail: null })
    })
  },

  onRetry() {
    this.loadDetail()
  },

  onGoPay() {
    var detail = this.data.detail
    if (!detail || !detail.registerId) return
    wx.navigateTo({
      url: billUtil.buildBillsUrl({ tab: 'pending', registerId: detail.registerId }),
    })
  },
})
