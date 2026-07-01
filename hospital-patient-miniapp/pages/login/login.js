const {
  getAccessToken,
  patientLogin,
  navigateAfterLogin,
  readAccounts,
  applySession,
} = require('../../utils/auth')
const { USE_MOCK } = require('../../config')
const { updateProfile } = require('../../api/patient')
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
    useMock: USE_MOCK,
    addMode: false,
    hasAccounts: false,
    navTitle: '登录',
    loading: false,
    today: todayStr(),
    genderLabels: GENDER_LABELS,
    genderIndex: 0,
    form: {
      realName: '',
      idCard: '',
      gender: 1,
      birthDate: '',
      phone: '',
      address: '',
    },
  },

  onLoad(options) {
    this.redirect = options.redirect ? decodeURIComponent(options.redirect) : ''
    this.setData({
      addMode: options.add === '1',
      hasAccounts: readAccounts().length > 0,
      navTitle: options.add === '1' ? '添加病人账户' : '登录',
    })
    if (!this.data.addMode && getAccessToken()) {
      navigateAfterLogin(this.redirect)
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

  goAddMode() {
    this.setData({ addMode: true, navTitle: '添加病人账户' })
  },

  async onLogin() {
    if (this.data.loading) return
    const errMsg = validateSelfProfile(this.data.form)
    if (errMsg) {
      wx.showToast({ title: errMsg, icon: 'none' })
      return
    }
    const payload = buildProfilePayload(this.data.form)
    this.setData({ loading: true })
    try {
      const data = await patientLogin(payload)
      let profileRes
      try {
        profileRes = await updateProfile(payload)
      } catch (profileErr) {
        wx.showModal({
          title: '档案保存失败',
          content: (profileErr.message || '请稍后在个人档案中完善') + '，您已登录成功。',
          showCancel: false,
          success: () => {
            navigateAfterLogin('/pages/profile/profile')
          },
        })
        return
      }
      const p = (profileRes && profileRes.data) || {}
      if (p.identityMerged && p.accessToken) {
        applySession({
          accessToken: p.accessToken,
          patientId: p.id,
          medicalRecordNo: p.medicalRecordNo,
          realName: p.realName || payload.realName,
        })
      } else if (data.realName || payload.realName) {
        applySession({
          accessToken: data.accessToken,
          patientId: data.patientId,
          medicalRecordNo: data.medicalRecordNo,
          realName: p.realName || data.realName || payload.realName,
        })
      }
      wx.showToast({
        title: p.identityMerged ? '登录成功，档案已合并' : (this.data.addMode ? '账户已添加' : '登录成功'),
        icon: 'success',
      })
      setTimeout(() => {
        navigateAfterLogin(this.redirect)
      }, 400)
    } catch (err) {
      wx.showModal({
        title: '登录失败',
        content: err.message || '请检查网络后重试',
        showCancel: false,
      })
    } finally {
      this.setData({ loading: false })
    }
  },
})
