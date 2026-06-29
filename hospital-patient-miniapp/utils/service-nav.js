const { fetchMyRegisters } = require('../api/patient')
const patientContext = require('./patient-context')
const { requireLogin } = require('./auth')
const { openReportsTab } = require('./report-nav')

const TAB_PAGE_PATHS = [
  '/pages/home/home',
  '/pages/reports/reports',
  '/pages/mine/mine',
]

function isTabPage(url) {
  if (!url) return false
  var path = url.split('?')[0]
  return TAB_PAGE_PATHS.indexOf(path) >= 0
}

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
  if (!requireLogin({ mode: 'modal', message: '查看候诊进度请先登录' })) return
  var active = patientContext.getActiveMember()
  fetchMyRegisters({ visitState: 1, patientId: active.memberPatientId }).then(function (res) {
    var list = (res && res.data && res.data.list) || []
    var reg = list[0]
    if (!reg) {
      wx.showModal({
        title: '候诊进度',
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
    showStubModal(item.name || '功能提示', item.stubMsg || (item.name + '暂未开通'))
    return
  }
  if (!requireLogin({
    mode: 'modal',
    message: '使用「' + (item.name || '该功能') + '」请先登录',
    redirect: item.action === 'navigate' && item.url ? item.url : '',
  })) {
    return
  }
  if (item.action === 'checkin') {
    goCheckin()
    return
  }
  if (item.action === 'openReports') {
    openReportsTab(item.reportTab || 'all')
    return
  }
  if (item.action === 'switchTab' && item.url) {
    wx.switchTab({ url: item.url })
    return
  }
  if (item.action === 'navigate' && item.url) {
    if (isTabPage(item.url)) {
      wx.showToast({ title: '请使用底部「报告」Tab 查看', icon: 'none' })
      return
    }
    wx.navigateTo({ url: item.url })
  }
}

module.exports = { handleService, showStub, showStubModal, goCheckin }
