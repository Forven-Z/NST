const { API_BASE, USE_MOCK } = require('../config')
const mockStore = require('../mock/store')
const accountStore = require('./account-store')
const { post } = require('./request')

const TAB_PATHS = ['/pages/home/home', '/pages/reports/reports', '/pages/mine/mine']

function getAccessToken() {
  var acc = accountStore.getCurrentAccount()
  return (acc && acc.accessToken) || wx.getStorageSync('accessToken') || ''
}

function isLoggedIn() {
  return !!getAccessToken()
}

function applySession(data) {
  accountStore.upsertAccount({
    patientId: data.patientId,
    accessToken: data.accessToken,
    medicalRecordNo: data.medicalRecordNo,
    realName: data.realName || data.realName,
  })
}

function clearSession() {
  accountStore.clearAllAccounts()
}

function requireLogin(options) {
  options = options || {}
  if (isLoggedIn()) return true
  var redirect = options.redirect || ''
  var url = '/pages/login/login'
  if (redirect) {
    url += '?redirect=' + encodeURIComponent(redirect)
  }
  if (options.mode === 'modal') {
    wx.showModal({
      title: '需要登录',
      content: options.message || '请先登录病人账户',
      confirmText: '去登录',
      cancelText: '暂不',
      success: function (res) {
        if (res.confirm) wx.navigateTo({ url: url })
      },
    })
  } else {
    wx.navigateTo({ url: url })
  }
  return false
}

function navigateAfterLogin(redirect) {
  if (!redirect) {
    wx.switchTab({ url: '/pages/home/home' })
    return
  }
  var path = redirect.split('?')[0]
  if (TAB_PATHS.indexOf(path) >= 0) {
    wx.switchTab({ url: path })
    return
  }
  wx.redirectTo({ url: redirect })
}

/** 病人账户登录（完整本人档案：姓名、身份证、性别、出生日期、手机号、地址） */
function patientLogin(credentials) {
  if (USE_MOCK) {
    return mockStore.patientLogin(credentials).then(function (res) {
      var data = res.data || {}
      applySession(data)
      return data
    })
  }
  return post('/patient/auth/login', credentials, false).then(function (payload) {
    var data = payload.data || {}
    applySession(data)
    return data
  })
}

/** QQ 式切换账户（后端换 JWT） */
function switchAccount(targetPatientId) {
  if (USE_MOCK) {
    return mockStore.switchAccount(targetPatientId).then(function (res) {
      var data = res.data || {}
      applySession(data)
      return data
    })
  }
  return post('/patient/auth/switch-account', { targetPatientId: targetPatientId }).then(function (payload) {
    var data = payload.data || {}
    applySession(data)
    return data
  })
}

/** 支付前绑定微信 openid 到当前病人账户 */
function bindWechatForPay() {
  if (USE_MOCK) {
    return Promise.resolve({ bound: true })
  }
  return new Promise(function (resolve, reject) {
    wx.login({
      success: function (loginRes) {
        var code = loginRes.code || 'dev-mock-' + Date.now()
        post('/patient/auth/wechat/bind', { code: code }).then(resolve).catch(reject)
      },
      fail: function (err) {
        reject(new Error(err.errMsg || 'wx.login 失败'))
      },
    })
  })
}

module.exports = {
  getAccessToken,
  isLoggedIn,
  applySession,
  clearSession,
  requireLogin,
  navigateAfterLogin,
  patientLogin,
  switchAccount,
  bindWechatForPay,
  readAccounts: accountStore.readAccounts,
  getCurrentAccount: accountStore.getCurrentAccount,
  removeAccount: accountStore.removeAccount,
}
