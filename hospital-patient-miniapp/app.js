const { getAccessToken, clearSession } = require('./utils/auth')

App({
  globalData: {
    patientId: null,
    medicalRecordNo: '',
  },

  onLaunch() {
    const token = getAccessToken()
    if (token) {
      this.globalData.patientId = wx.getStorageSync('patientId') || null
      this.globalData.medicalRecordNo = wx.getStorageSync('medicalRecordNo') || ''
    } else {
      clearSession()
    }
  },

  ensureLogin() {
    if (!getAccessToken()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return false
    }
    return true
  },
})
