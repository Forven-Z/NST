const { fetchProfile, updateProfile } = require('../../api/patient')
const { clearSession, setSession } = require('../../utils/auth')
const patientContext = require('../../utils/patient-context')

Page({
  data: {
    loading: false,
    saving: false,
    form: {
      realName: '',
      gender: 1,
      phone: '',
      idCard: '',
      address: '',
    },
    genderOptions: ['未知', '男', '女'],
    medicalRecordNo: '',
  },

  onShow() {
    this.loadProfile()
  },

  async loadProfile() {
    this.setData({ loading: true })
    try {
      const res = await fetchProfile()
      const p = res.data || {}
      this.setData({
        medicalRecordNo: p.medicalRecordNo || '',
        form: {
          realName: p.realName || '',
          gender: p.gender == null ? 1 : p.gender,
          phone: p.phone || '',
          idCard: p.idCard || '',
          address: p.address || '',
        },
      })
    } catch (err) {
      wx.showToast({ title: err.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },

  onGenderChange(e) {
    this.setData({ 'form.gender': Number(e.detail.value) })
  },

  async onSave() {
    if (this.data.saving) return
    this.setData({ saving: true })
    try {
      const res = await updateProfile(this.data.form)
      const p = res.data || {}
      if (p.identityMerged && p.accessToken) {
        setSession({
          accessToken: p.accessToken,
          patientId: p.id,
          medicalRecordNo: p.medicalRecordNo,
        })
        patientContext.clearActiveMember()
        patientContext.setOwnerFromLogin({
          patientId: p.id,
          realName: p.realName,
          medicalRecordNo: p.medicalRecordNo,
        })
        wx.showToast({ title: '档案已合并', icon: 'success' })
      } else {
        wx.showToast({ title: '已保存', icon: 'success' })
      }
    } catch (err) {
      wx.showToast({ title: err.message || '保存失败', icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前账号？',
      success(res) {
        if (res.confirm) {
          clearSession()
          wx.reLaunch({ url: '/pages/login/login' })
        }
      },
    })
  },
})
