function pad(n) {
  return String(n).padStart(2, '0')
}

function formatDate(d) {
  const dt = d instanceof Date ? d : new Date(d)
  return `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())}`
}

function nextDays(count) {
  const list = []
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  for (let i = 0; i < count; i += 1) {
    const d = new Date(today)
    d.setDate(d.getDate() + i)
    const weekday = d.getDay()
    if (weekday === 0) continue
    const labels = ['日', '一', '二', '三', '四', '五', '六']
    list.push({
      workDate: formatDate(d),
      weekday,
      label: i === 0 ? '今天' : `周${labels[weekday]}`,
      isSaturday: weekday === 6,
    })
  }
  return list
}

/** 兼容后端 LocalDate 字符串或 [y,m,d] 数组 */
function formatDisplayDate(value) {
  if (value == null || value === '') return '—'
  if (typeof value === 'string') return value.slice(0, 10)
  if (Array.isArray(value) && value.length >= 3) {
    return `${value[0]}-${pad(value[1])}-${pad(value[2])}`
  }
  if (typeof value === 'object' && value.year != null) {
    return `${value.year}-${pad(value.monthValue || value.month)}-${pad(value.dayOfMonth || value.day)}`
  }
  return String(value)
}

module.exports = { formatDate, formatDisplayDate, nextDays }
