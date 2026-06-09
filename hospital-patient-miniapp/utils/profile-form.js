const { isValidPhone, normalizePhone, normalizePhoneOptional } = require('./phone')
const { isValidIdCard, parseIdCard } = require('./id-card')

const GENDER_LABELS = ['男', '女']
const GENDER_VALUES = [1, 2]

function genderIndexOf(value) {
  const idx = GENDER_VALUES.indexOf(value)
  return idx >= 0 ? idx : 0
}

function todayStr() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

/** 本人档案校验；requirePhone 默认 true（登录/个人档案） */
function validateSelfProfile(form, options) {
  options = options || {}
  const requirePhone = options.requirePhone !== false
  if (!form.realName || !String(form.realName).trim()) {
    return '请填写姓名'
  }
  if (!isValidIdCard(form.idCard)) {
    return '请填写18位身份证号'
  }
  if (!form.gender || (form.gender !== 1 && form.gender !== 2)) {
    return '请选择性别'
  }
  if (!form.birthDate) {
    return '请选择出生日期'
  }
  if (requirePhone) {
    if (!isValidPhone(form.phone)) {
      return '请填写11位手机号'
    }
  } else if (normalizePhoneOptional(form.phone) === null) {
    return '手机号格式不正确'
  }
  return null
}

function buildProfilePayload(form, options) {
  options = options || {}
  const requirePhone = options.requirePhone !== false
  const phone = requirePhone
    ? normalizePhone(form.phone)
    : normalizePhoneOptional(form.phone)
  return {
    realName: String(form.realName).trim(),
    idCard: String(form.idCard).trim().toUpperCase(),
    gender: form.gender,
    birthDate: form.birthDate,
    phone: phone || '',
    address: String(form.address || '').trim(),
  }
}

module.exports = {
  GENDER_LABELS,
  GENDER_VALUES,
  genderIndexOf,
  todayStr,
  validateSelfProfile,
  buildProfilePayload,
  parseIdCard,
}
