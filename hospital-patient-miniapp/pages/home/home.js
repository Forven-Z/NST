const { fetchProfile } = require('../../api/patient')
const { getAccessToken } = require('../../utils/auth')

Page({
  data: {
    profile: null,
    medicalRecordNo: '',
  },

  onShow() {
    if (!getAccessToken()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    this.loadProfile()
  },

  async loadProfile() {
    try {
      const res = await fetchProfile()
      const profile = res.data || {}
      this.setData({
        profile,
        medicalRecordNo: profile.medicalRecordNo || wx.getStorageSync('medicalRecordNo') || '',
      })
    } catch (err) {
      wx.showToast({ title: err.message || '加载失败', icon: 'none' })
    }
  },

  goRegister() {
    wx.navigateTo({ url: '/pages/register/register' })
  },

  goBills() {
    wx.navigateTo({ url: '/pages/bills/bills' })
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/profile/profile' })
  },

  goMedicalRecord() {
    wx.navigateTo({ url: '/pages/medical-record/medical-record' })
  },
})
