/** 从 18 位身份证号解析性别与出生日期 */
function parseIdCard(idCard) {
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

function isValidIdCard(idCard) {
  return /^\d{17}[\dX]$/i.test(String(idCard || '').trim())
}

module.exports = {
  parseIdCard,
  isValidIdCard,
}
