const { fetchProfile, updateProfile } = require('../../api/patient')
const { clearSession, applySession } = require('../../utils/auth')
const {
  GENDER_LABELS,
  GENDER_VALUES,
  genderIndexOf,
  todayStr,
  validateSelfProfile,
  buildProfilePayload,
  parseIdCard,
} = require('../../utils/profile-form')

Page({
  data: {
    loading: false,
    saving: false,
    today: todayStr(),
    genderLabels: GENDER_LABELS,
    genderIndex: 0,
    form: {
      realName: '',
      gender: 1,
      birthDate: '',
      phone: '',
      idCard: '',
      address: '',
    },
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
      const gender = p.gender === 2 ? 2 : 1
      this.setData({
        medicalRecordNo: p.medicalRecordNo || '',
        genderIndex: genderIndexOf(gender),
        form: {
          realName: p.realName || '',
          gender: gender,
          birthDate: p.birthDate || '',
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
    const idx = Number(e.detail.value)
    this.setData({
      genderIndex: idx,
      'form.gender': GENDER_VALUES[idx],
    })
  },

  onBirthDateChange(e) {
    this.setData({ 'form.birthDate': e.detail.value })
  },

  onIdCardBlur() {
    const parsed = parseIdCard(this.data.form.idCard)
    if (!parsed) return
    this.setData({
      'form.birthDate': parsed.birthDate,
      'form.gender': parsed.gender,
      genderIndex: genderIndexOf(parsed.gender),
    })
  },

  async onSave() {
    if (this.data.saving) return
    const errMsg = validateSelfProfile(this.data.form)
    if (errMsg) {
      wx.showToast({ title: errMsg, icon: 'none' })
      return
    }
    const payload = buildProfilePayload(this.data.form)
    this.setData({ saving: true })
    try {
      const res = await updateProfile(payload)
      const p = res.data || {}
      if (p.identityMerged && p.accessToken) {
        applySession({
          accessToken: p.accessToken,
          patientId: p.id,
          medicalRecordNo: p.medicalRecordNo,
          realName: p.realName,
        })
        wx.showToast({ title: '档案已合并', icon: 'success' })
      } else {
        wx.showToast({ title: '已保存', icon: 'success' })
      }
      this.loadProfile()
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
          wx.switchTab({ url: '/pages/home/home' })
        }
      },
    })
  },
})
