const { API_BASE, USE_MOCK } = require('../config')
const accountStore = require('./account-store')

function request(options) {
  const { url, method = 'GET', data, auth = true } = options
  const header = { 'Content-Type': 'application/json' }

  if (auth) {
    const acc = accountStore.getCurrentAccount()
    const token = (acc && acc.accessToken) || wx.getStorageSync('accessToken') || ''
    if (token) {
      header.Authorization = `Bearer ${token}`
    }
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${API_BASE}${url}`,
      method,
      data,
      header,
      timeout: 15000,
      success(res) {
        if (res.statusCode === 502 || res.statusCode === 503) {
          reject(new Error('服务暂不可用，请稍后重试或联系管理员'))
          return
        }
        const payload = res.data
        if (typeof payload !== 'object' || payload === null) {
          reject(new Error(`HTTP ${res.statusCode}：后端无有效 JSON 响应`))
          return
        }
        if (payload.success === false) {
          if (payload.code === 401) {
            accountStore.clearAllAccounts()
            wx.showToast({ title: '请先登录', icon: 'none' })
            setTimeout(function () {
              wx.navigateTo({ url: '/pages/login/login' })
            }, 300)
          }
          reject(new Error(payload.message || '请求失败'))
          return
        }
        resolve(payload)
      },
      fail(err) {
        const msg = err.errMsg || '网络异常'
        if (!USE_MOCK && (msg.indexOf('fail') >= 0 || msg.indexOf('timeout') >= 0)) {
          reject(new Error('无法连接服务器，请检查网络后重试'))
          return
        }
        reject(new Error(msg))
      },
    })
  })
}

function get(url, data) {
  return request({ url, method: 'GET', data })
}

function post(url, data, auth) {
  return request({ url, method: 'POST', data, auth: auth !== false })
}

function put(url, data) {
  return request({ url, method: 'PUT', data })
}

module.exports = {
  request,
  get,
  post,
  put,
}
