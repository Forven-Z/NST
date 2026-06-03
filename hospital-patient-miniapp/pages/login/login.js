const { getAccessToken, wechatLogin } = require('../../utils/auth')

Page({
  data: {
    nickName: '测试患者',
    loading: false,
  },

  onLoad() {
    if (getAccessToken()) {
      wx.reLaunch({ url: '/pages/home/home' })
    }
  },

  onNickInput(e) {
    this.setData({ nickName: e.detail.value })
  },

  async onLogin() {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const data = await wechatLogin(this.data.nickName.trim() || '微信用户')
      const app = getApp()
      app.globalData.patientId = data.patientId
      app.globalData.medicalRecordNo = data.medicalRecordNo || ''
      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        wx.reLaunch({ url: '/pages/home/home' })
      }, 400)
    } catch (err) {
      wx.showModal({
        title: '登录失败',
        content: err.message || '请确认 Gateway :9000 已启动',
        showCancel: false,
      })
    } finally {
      this.setData({ loading: false })
    }
  },
})
