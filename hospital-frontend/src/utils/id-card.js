/** 从 18 位身份证号解析性别与出生日期（与小程序 utils/id-card.js 一致） */
export function parseIdCard(idCard) {
  const s = String(idCard || '').trim().toUpperCase()
  if (!/^\d{17}[\dX]$/.test(s)) {
    return null
  }
  const y = s.slice(6, 10)
  const m = s.slice(10, 12)
  const d = s.slice(12, 14)
  const genderCode = parseInt(s.charAt(16), 10)
  return {
    birthDate: `${y}-${m}-${d}`,
    gender: genderCode % 2 === 1 ? 1 : 2,
  }
}

export function isValidIdCard(idCard) {
  return /^\d{17}[\dX]$/i.test(String(idCard || '').trim())
}

/** 按周岁计算年龄 */
export function calcAgeFromBirthDate(birthDateStr) {
  if (!birthDateStr) return null
  const parts = birthDateStr.split('-').map(Number)
  if (parts.length !== 3 || parts.some((n) => Number.isNaN(n))) return null
  const [y, m, d] = parts
  const today = new Date()
  let age = today.getFullYear() - y
  const month = today.getMonth() + 1
  const day = today.getDate()
  if (month < m || (month === m && day < d)) age -= 1
  return age >= 0 ? age : null
}

export function applyIdCardToForm(idCard, form) {
  const parsed = parseIdCard(idCard)
  if (!parsed) return false
  form.gender = parsed.gender
  form.birthDate = parsed.birthDate
  const age = calcAgeFromBirthDate(parsed.birthDate)
  if (age != null) form.age = String(age)
  return true
}
