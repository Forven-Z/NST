const { addFamilyMember } = require('../../../api/patient')
const { switchAccount } = require('../../../utils/auth')
const { RELATION_TYPES } = require('../../../utils/visit-state')
const { normalizePhoneOptional, isValidPhone } = require('../../../utils/phone')
const { parseIdCard, isValidIdCard } = require('../../../utils/id-card')

const GENDER_LABELS = ['男', '女']
const GENDER_VALUES = [1, 2]

function genderIndexOf(value) {
  const idx = GENDER_VALUES.indexOf(value)
  return idx >= 0 ? idx : 0
}

function relationIndexOf(types, value) {
  const idx = types.findIndex(function (t) { return t.value === value })
  return idx >= 0 ? idx : 0
}

function todayStr() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

Page({
  data: {
    saving: false,
    today: todayStr(),
    genderLabels: GENDER_LABELS,
    genderIndex: 0,
    relationTypes: RELATION_TYPES,
    relationIndex: 3,
    form: {
      realName: '',
      idCard: '',
      phone: '',
      gender: 1,
      birthDate: '',
      address: '',
      relationType: 4,
      noIdCard: false,
      guardianName: '',
      guardianIdCard: '',
      guardianPhone: '',
    },
  },

  onLoad() {
    this.prefillGuardian()
  },

  async prefillGuardian() {
    try {
      const res = await fetchProfile()
      const p = res.data || {}
      this.setData({
        'form.guardianName': p.realName || '',
        'form.guardianIdCard': p.idCard || '',
        'form.guardianPhone': p.phone || '',
      })
    } catch (err) {
      // 游客或未登录不应进入此页；忽略预填失败
    }
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },

  onNoIdCardChange(e) {
    const noIdCard = !!e.detail.value
    const relationType = noIdCard ? 3 : 4
    this.setData({
      'form.noIdCard': noIdCard,
      'form.relationType': relationType,
      'form.idCard': noIdCard ? '' : this.data.form.idCard,
      'form.phone': noIdCard ? '' : this.data.form.phone,
      relationIndex: relationIndexOf(this.data.relationTypes, relationType),
    })
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

  onRelationChange(e) {
    const idx = Number(e.detail.value)
    const item = this.data.relationTypes[idx]
    this.setData({
      relationIndex: idx,
      'form.relationType': item.value,
    })
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

  async onSubmit() {
    const form = this.data.form
    if (!form.realName.trim()) {
      wx.showToast({ title: '请填写姓名', icon: 'none' })
      return
    }
    if (!form.birthDate) {
      wx.showToast({ title: '请选择出生日期', icon: 'none' })
      return
    }
    if (!form.gender) {
      wx.showToast({ title: '请选择性别', icon: 'none' })
      return
    }

    let payload
    if (form.noIdCard) {
      if (!form.guardianName.trim() || !form.guardianIdCard.trim() || !form.guardianPhone.trim()) {
        wx.showToast({ title: '请填写陪诊人信息', icon: 'none' })
        return
      }
      if (!isValidIdCard(form.guardianIdCard)) {
        wx.showToast({ title: '陪诊人身份证格式不正确', icon: 'none' })
        return
      }
      if (!isValidPhone(form.guardianPhone)) {
        wx.showToast({ title: '陪诊人手机号格式不正确', icon: 'none' })
        return
      }
      payload = {
        realName: form.realName.trim(),
        gender: form.gender,
        birthDate: form.birthDate,
        address: form.address.trim(),
        relationType: form.relationType,
        noIdCard: true,
        guardianName: form.guardianName.trim(),
        guardianIdCard: form.guardianIdCard.trim().toUpperCase(),
        guardianPhone: form.guardianPhone.trim(),
      }
    } else {
      if (!isValidIdCard(form.idCard)) {
        wx.showToast({ title: '请填写18位身份证号', icon: 'none' })
        return
      }
      const normalized = normalizePhoneOptional(form.phone)
      if (normalized === null) {
        wx.showToast({ title: '手机号格式不正确', icon: 'none' })
        return
      }
      payload = {
        realName: form.realName.trim(),
        idCard: form.idCard.trim().toUpperCase(),
        gender: form.gender,
        birthDate: form.birthDate,
        phone: normalized,
        address: form.address.trim(),
        relationType: form.relationType,
        noIdCard: false,
      }
    }

    this.setData({ saving: true })
    try {
      const res = await addFamilyMember(payload)
      const member = (res && res.data) || {}
      wx.showModal({
        title: '添加成功',
        content: '是否切换到该就诊账户？',
        confirmText: '切换',
        cancelText: '稍后',
        success: function (r) {
          if (r.confirm && member.memberPatientId) {
            switchAccount(member.memberPatientId).then(function () {
              wx.navigateBack()
            }).catch(function () {
              wx.navigateBack()
            })
          } else {
            wx.navigateBack()
          }
        },
      })
    } catch (err) {
      wx.showToast({ title: err.message || '添加失败', icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  },
})
