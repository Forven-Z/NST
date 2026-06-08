const { fetchReportDetail } = require('../../../api/patient')

Page({
  data: {
    loading: true,
    detail: null,
  },

  onLoad(options) {
    this.type = options.type
    this.requestId = options.requestId
    this.loadDetail()
  },

  loadDetail() {
    var that = this
    if (!this.type || !this.requestId) {
      wx.showToast({ title: '参数错误', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    fetchReportDetail(this.type, this.requestId).then(function (res) {
      var detail = (res && res.data) || null
      if (detail) {
        if (detail.type === 'disposal') {
          detail.applySectionTitle = '申请信息'
          detail.purposeLabel = '处置目的'
          detail.contentSectionTitle = '处置记录'
          detail.timeLabel = '记录时间'
        } else if (detail.type === 'lab') {
          detail.applySectionTitle = '申请信息'
          detail.purposeLabel = '检验目的'
          detail.contentSectionTitle = '检验报告'
          detail.timeLabel = '报告时间'
        } else {
          detail.applySectionTitle = '申请信息'
          detail.purposeLabel = '检查目的'
          detail.contentSectionTitle = '检查报告'
          detail.timeLabel = '报告时间'
        }
      }
      that.setData({ detail: detail, loading: false })
      if (detail && detail.reportName) {
        wx.setNavigationBarTitle({ title: detail.reportName })
      }
    }).catch(function (err) {
      that.setData({ loading: false })
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    })
  },
})
