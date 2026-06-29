const { bindWechatForPay } = require('./auth')
const { mockPayment } = require('../api/patient')

/**
 * 演示环境：模拟微信支付流程（不调真实收银，UI 接近真支付）
 */
function runSimulatedWechatPay(options) {
  var billIds = options.billIds || []
  var amount = Number(options.amount) || 0
  var onSuccess = options.onSuccess
  var onFail = options.onFail

  if (!billIds.length) {
    wx.showToast({ title: '请选择账单', icon: 'none' })
    return Promise.resolve()
  }

  wx.showLoading({ title: '正在调起微信支付…', mask: true })

  return bindWechatForPay().catch(function () {
    return { bound: false }
  }).then(function () {
    return new Promise(function (resolve) {
      setTimeout(resolve, 500)
    })
  }).then(function () {
    wx.hideLoading()
    return new Promise(function (resolve, reject) {
      wx.showModal({
        title: '微信支付',
        content: '确认支付 ¥' + amount.toFixed(2) + '？',
        confirmText: '确认支付',
        confirmColor: '#07c160',
        cancelText: '取消',
        success: function (res) {
          if (!res.confirm) {
            reject(new Error('已取消支付'))
            return
          }
          wx.showLoading({ title: '支付处理中…', mask: true })
          mockPayment(billIds).then(function (payRes) {
            wx.hideLoading()
            wx.showToast({ title: '支付成功', icon: 'success', duration: 2000 })
            resolve(payRes)
          }).catch(function (err) {
            wx.hideLoading()
            reject(err)
          })
        },
      })
    })
  }).then(function (res) {
    if (onSuccess) onSuccess(res)
    return res
  }).catch(function (err) {
    if (err && err.message && err.message !== '已取消支付') {
      wx.showToast({ title: err.message || '支付失败', icon: 'none' })
    }
    if (onFail) onFail(err)
    throw err
  })
}

module.exports = {
  runSimulatedWechatPay,
}
