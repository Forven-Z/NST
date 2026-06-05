const { fetchFamilyMembers } = require('../../api/patient')

Page({
  data: {
    list: [],
    loading: false,
  },

  onShow() {
    this.load()
  },

  async load() {
    this.setData({ loading: true })
    try {
      const res = await fetchFamilyMembers()
      this.setData({ list: res.data?.list || [] })
    } catch (err) {
      wx.showToast({ title: err.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  goAdd() {
    wx.navigateTo({ url: '/pages/cards/add/add' })
  },
})
