const { getAccessToken, clearSession } = require('./utils/auth')

App({
  globalData: {
    ownerPatientId: null,
    activeMemberPatientId: null,
    medicalRecordNo: '',
  },

  onLaunch() {
    const token = getAccessToken()
    if (token) {
      this.globalData.ownerPatientId = wx.getStorageSync('patientId') || null
      this.globalData.medicalRecordNo = wx.getStorageSync('medicalRecordNo') || ''
      this.globalData.activeMemberPatientId = wx.getStorageSync('activeMemberPatientId') || this.globalData.ownerPatientId
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
