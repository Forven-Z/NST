const { fetchQueueStatus } = require('../../api/patient')

Page({
  data: {
    loading: false,
    registerId: '',
    info: null,
  },

  onLoad(options) {
    this.setData({ registerId: options.registerId || '' })
    if (options.registerId) this.load()
  },

  async load() {
    this.setData({ loading: true })
    try {
      const res = await fetchQueueStatus(this.data.registerId)
      this.setData({ info: res.data })
    } catch (err) {
      wx.showToast({ title: err.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },
})
