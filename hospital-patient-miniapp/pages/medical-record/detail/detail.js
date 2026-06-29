const { fetchVisitHub } = require('../../../api/visits')
const { buildRecordSections, buildDiseaseNames } = require('../../../utils/medical-record-sections')
const { mapOrderList, handleOrderAction } = require('../../../utils/order-action')

Page({
  data: {
    registerId: '',
    loading: true,
    loadError: false,
    summary: null,
    hasMedicalRecord: false,
    medicalRecordStatus: null,
    sections: [],
    diseaseNames: '',
    orders: [],
    activeAnchor: 'record',
  },

  onLoad(options) {
    this.registerId = options.registerId
    if (!this.registerId) {
      wx.showToast({ title: '参数错误', icon: 'none' })
      this.setData({ loading: false })
      return
    }
    this.setData({ registerId: this.registerId })
    this.loadHub()
  },

  onPullDownRefresh() {
    this.loadHub().finally(function () {
      wx.stopPullDownRefresh()
    })
  },

  loadHub() {
    var that = this
    this.setData({ loading: true, loadError: false })
    return fetchVisitHub(Number(this.registerId)).then(function (res) {
      var data = (res && res.data) || {}
      var summary = data.registerSummary || null
      var record = data.medicalRecord || null
      var ordersData = data.orders || {}
      var orders = mapOrderList(ordersData.list || [])
      that.setData({
        summary: summary,
        hasMedicalRecord: !!data.hasMedicalRecord,
        medicalRecordStatus: data.medicalRecordStatus,
        sections: buildRecordSections(record),
        diseaseNames: buildDiseaseNames(record),
        orders: orders,
        loading: false,
        loadError: false,
      })
      if (summary && summary.visitDateLabel) {
        wx.setNavigationBarTitle({ title: summary.visitDateLabel + ' 就诊' })
      } else {
        wx.setNavigationBarTitle({ title: '就诊记录' })
      }
    }).catch(function (err) {
      that.setData({ loading: false, loadError: true })
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    })
  },

  onRetry() {
    this.loadHub()
  },

  onAnchorTap(e) {
    var anchor = e.currentTarget.dataset.anchor
    if (!anchor) return
    this.setData({ activeAnchor: anchor })
    wx.pageScrollTo({
      selector: '#section-' + anchor,
      duration: 280,
    })
  },

  onOrderAction(e) {
    var item = e.currentTarget.dataset.item
    handleOrderAction(item, this.registerId)
  },
})
