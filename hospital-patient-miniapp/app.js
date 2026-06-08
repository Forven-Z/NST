const { getAccessToken, clearSession } = require('./utils/auth')
const accountStore = require('./utils/account-store')

App({
  globalData: {
    currentPatientId: null,
    medicalRecordNo: '',
  },

  onLaunch() {
    const acc = accountStore.getCurrentAccount()
    if (acc && acc.accessToken) {
      this.globalData.currentPatientId = acc.patientId
      this.globalData.medicalRecordNo = acc.medicalRecordNo || ''
    } else if (getAccessToken()) {
      this.globalData.currentPatientId = wx.getStorageSync('patientId') || null
      this.globalData.medicalRecordNo = wx.getStorageSync('medicalRecordNo') || ''
    } else {
      clearSession()
    }
  },

  ensureLogin(options) {
    const { requireLogin } = require('./utils/auth')
    return requireLogin(options || { mode: 'modal' })
  },
})
