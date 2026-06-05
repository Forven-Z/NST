const { fetchReports } = require('../../api/patient')

Page({
  data: {
    tab: 'all',
    list: [],
    loading: false,
  },

  onLoad(options) {
    var tab = 'all'
    if (options.type === 'lab' || options.type === 'exam') {
      tab = options.type
    }
    this.setData({ tab: tab })
    this.load()
  },

  onTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab })
    this.load()
  },

  async load() {
    this.setData({ loading: true })
    try {
      const res = await fetchReports({ type: this.data.tab })
      this.setData({ list: (res.data && res.data.list) || [] })
    } catch (err) {
      wx.showToast({ title: err.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  onDetail(e) {
    const item = e.currentTarget.dataset.item
    wx.showModal({
      title: item.reportName,
      content: item.summary || '暂无详细内容',
      showCancel: false,
    })
  },
})
