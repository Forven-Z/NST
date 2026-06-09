function normalizePhone(phone) {
  return String(phone || '').replace(/\s/g, '')
}

function isValidPhone(phone) {
  return /^1\d{10}$/.test(normalizePhone(phone))
}

/** 有值则校验格式并归一化；空则返回 ''（儿童等可无手机号） */
function normalizePhoneOptional(phone) {
  const normalized = normalizePhone(phone)
  if (!normalized) return ''
  if (!isValidPhone(normalized)) {
    return null
  }
  return normalized
}

module.exports = {
  normalizePhone,
  isValidPhone,
  normalizePhoneOptional,
}
