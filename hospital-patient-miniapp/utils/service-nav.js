const { fetchMyRegisters } = require('../api/patient')
const patientContext = require('./patient-context')

function showStub(msg) {
  wx.showToast({ title: msg || '功能即将上线', icon: 'none', duration: 2500 })
}

function showStubModal(title, msg) {
  wx.showModal({
    title: title || '提示',
    content: msg || '功能即将上线',
    showCancel: false,
  })
}

function goCheckin() {
  var active = patientContext.getActiveMember()
  fetchMyRegisters({ visitState: 1, patientId: active.memberPatientId }).then(function (res) {
    var list = (res && res.data && res.data.list) || []
    var reg = list[0]
    if (!reg) {
      wx.showModal({
        title: '排队候诊',
        content: '当前就诊人暂无已缴费待就诊的挂号，请先完成挂号并支付。',
        confirmText: '去挂号',
        success: function (r) {
          if (r.confirm) wx.navigateTo({ url: '/pages/register/register' })
        },
      })
      return
    }
    wx.navigateTo({ url: '/pages/queue/queue?registerId=' + reg.registerId })
  }).catch(function (err) {
    showStub((err && err.message) || '加载失败')
  })
}

function handleService(item) {
  if (!item) return
  if (item.action === 'stub') {
    showStub(item.stubMsg || (item.name + '暂未开通'))
    return
  }
  if (item.action === 'checkin') {
    goCheckin()
    return
  }
  if (item.action === 'switchTab' && item.url) {
    wx.switchTab({ url: item.url })
    return
  }
  if (item.action === 'navigate' && item.url) {
    wx.navigateTo({ url: item.url })
  }
}

module.exports = { handleService, showStub, goCheckin }
