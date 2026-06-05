const { fetchMessages } = require('../../api/patient')
const { getAccessToken } = require('../../utils/auth')

Page({
  data: {
    list: [],
    loading: true,
  },

  onShow() {
    if (!getAccessToken()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    this.load()
  },

  load() {
    var that = this
    this.setData({ loading: true })
    fetchMessages().then(function (res) {
      that.setData({
        list: (res && res.data && res.data.list) || [],
        loading: false,
      })
    }).catch(function (err) {
      that.setData({ loading: false })
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    })
  },

  onTap(e) {
    var link = e.currentTarget.dataset.link
    if (!link) return
    if (link.indexOf('/pages/messages/') === 0 || link.indexOf('/pages/home/') === 0 || link.indexOf('/pages/mine/') === 0) {
      wx.switchTab({ url: link.split('?')[0] })
      return
    }
    wx.navigateTo({ url: link })
  },
})
