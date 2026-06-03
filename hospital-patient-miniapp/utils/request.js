const { API_BASE } = require('../config')
const { getAccessToken, clearSession } = require('./auth')

function request(options) {
  const { url, method = 'GET', data, auth = true } = options
  const header = { 'Content-Type': 'application/json' }

  if (auth) {
    const token = getAccessToken()
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
      success(res) {
        const payload = res.data
        if (payload && payload.success === false) {
          if (payload.code === 401) {
            clearSession()
            wx.reLaunch({ url: '/pages/login/login' })
          }
          reject(new Error(payload.message || '请求失败'))
          return
        }
        resolve(payload)
      },
      fail(err) {
        reject(new Error(err.errMsg || '网络异常'))
      },
    })
  })
}

function get(url, data) {
  return request({ url, method: 'GET', data })
}

function post(url, data) {
  return request({ url, method: 'POST', data })
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
