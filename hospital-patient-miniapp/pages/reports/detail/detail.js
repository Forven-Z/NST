const { fetchReportDetail } = require('../../../api/patient')
const { loadExamSnapshots } = require('../../../utils/report-snapshot')

Page({
  data: {
    loading: true,
    detail: null,
    snapshotViews: [],
    hasSnapshots: false,
    snapshotsLoading: false,
    snapshotsLoaded: false,
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
    this.setData({ loading: true, snapshotViews: [], hasSnapshots: false, snapshotsLoaded: false })
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
        } else if (detail.type === 'exam') {
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
      if (detail && (detail.reportType === 'check' || detail.type === 'exam')) {
        that.loadSnapshots(detail)
      }
    }).catch(function (err) {
      that.setData({ loading: false })
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    })
  },

  loadSnapshots(detail) {
    var that = this
    this.setData({ snapshotsLoading: true })
    loadExamSnapshots(detail).then(function (result) {
      that.setData({
        snapshotViews: result.snapshotViews || [],
        hasSnapshots: !!result.hasSnapshots,
        snapshotsLoading: false,
        snapshotsLoaded: true,
      })
      if (result.snapshotViews && result.snapshotViews.length === 0
          && detail.findings && detail.findings.hasSnapshots) {
        wx.showToast({ title: '影像加载失败，请重试', icon: 'none' })
      }
    }).catch(function () {
      that.setData({
        snapshotsLoading: false,
        hasSnapshots: false,
        snapshotViews: [],
        snapshotsLoaded: true,
      })
      wx.showToast({ title: '影像加载失败', icon: 'none' })
    })
  },

  onPreviewSnapshot(e) {
    var index = e.currentTarget.dataset.index
    var views = this.data.snapshotViews || []
    if (!views.length) return
    var urls = views.map(function (item) { return item.src }).filter(Boolean)
    if (!urls.length) return
    var current = views[index] && views[index].src ? views[index].src : urls[0]
    wx.previewImage({ current: current, urls: urls })
  },
})
