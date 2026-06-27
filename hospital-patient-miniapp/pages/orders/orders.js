const { fetchRegisterOrders } = require('../../api/orders')

Page({
  data: {
    registerId: null,
    list: [],
    loading: false,
  },

  onLoad(options) {
    this.setData({ registerId: options.registerId })
  },

  onShow() {
    if (this.data.registerId) this.load()
  },

  load() {
    var that = this
    this.setData({ loading: true })
    fetchRegisterOrders(this.data.registerId).then(function (res) {
      that.setData({
        list: (res && res.data && res.data.list) || [],
        loading: false,
      })
    }).catch(function (err) {
      that.setData({ loading: false })
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    })
  },

  goReport(e) {
    var item = e.currentTarget.dataset.item
    if (!item || item.status < 40) {
      wx.showToast({ title: '结果尚未出具', icon: 'none' })
      return
    }
    if (item.kind === 'prescription') {
      var tips = {
        10: '请前往缴费',
        15: '处方费用已退回，请等待医生修改后重新缴费',
        20: '请至药房取药',
        30: '处方已发药',
      }
      wx.showToast({ title: tips[item.status] || '请稍后查看或联系窗口', icon: 'none' })
      return
    }
    var type = item.kind === 'inspection' ? 'lab' : item.kind === 'check' ? 'exam' : 'disposal'
    wx.navigateTo({
      url: '/pages/reports/detail/detail?type=' + type + '&requestId=' + item.requestId,
    })
  },
})
