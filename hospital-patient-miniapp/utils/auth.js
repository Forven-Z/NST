const { API_BASE, USE_MOCK } = require('../config')
const mockStore = require('../mock/store')
const patientContext = require('./patient-context')

const TOKEN_KEY = 'accessToken'

function getAccessToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function setSession(data) {
  wx.setStorageSync(TOKEN_KEY, data.accessToken || '')
  wx.setStorageSync('patientId', data.patientId || '')
  wx.setStorageSync('medicalRecordNo', data.medicalRecordNo || '')
}

function clearSession() {
  wx.removeStorageSync(TOKEN_KEY)
  wx.removeStorageSync('patientId')
  wx.removeStorageSync('medicalRecordNo')
  patientContext.clearActiveMember()
}

function wechatLogin(nickName) {
  if (USE_MOCK) {
    return mockStore.login(nickName).then((res) => {
      const data = res.data || {}
      setSession(data)
      mockStore.setProfileFromLogin(data)
      patientContext.setOwnerFromLogin({
        patientId: data.patientId,
        realName: nickName || '微信用户',
        medicalRecordNo: data.medicalRecordNo,
      })
      return data
    })
  }
  return new Promise((resolve, reject) => {
    wx.login({
      success(loginRes) {
        const code = loginRes.code || `dev-mock-${Date.now()}`
        wx.request({
          url: `${API_BASE}/patient/auth/wechat`,
          method: 'POST',
          header: { 'Content-Type': 'application/json' },
          data: { code, nickName: nickName || '微信用户' },
          success(res) {
            const payload = res.data
            if (!payload || payload.success === false) {
              reject(new Error((payload && payload.message) || '登录失败'))
              return
            }
            setSession(payload.data || {})
            resolve(payload.data)
          },
          fail(err) {
            reject(new Error(err.errMsg || '网络异常'))
          },
        })
      },
      fail(err) {
        reject(new Error(err.errMsg || 'wx.login 失败'))
      },
    })
  })
}

module.exports = {
  getAccessToken,
  setSession,
  clearSession,
  wechatLogin,
}
