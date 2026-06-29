Page({
  data: {
    refund: null,
  },

  onLoad(options) {
    var payload = options.payload
    if (!payload) return
    try {
      var refund = JSON.parse(decodeURIComponent(payload))
      wx.setNavigationBarTitle({ title: '退款 #' + (refund.refundId || '') })
      this.setData({ refund: refund })
    } catch (e) {
      wx.showToast({ title: '参数错误', icon: 'none' })
    }
  },
})
