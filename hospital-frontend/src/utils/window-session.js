/** 窗口挂号：当天 + 当前午别（与后端 NoonTypeSupport 规则一致） */

export function formatLocalDate(date = new Date()) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function resolveWindowNoonType(date = new Date()) {
  const h = date.getHours()
  if (h < 13) return 1
  if (h < 18) return 2
  return 3
}

const NOON_LABELS = { 1: '上午', 2: '下午', 3: '晚上' }

export function getWindowSessionContext(date = new Date()) {
  const noonType = resolveWindowNoonType(date)
  return {
    workDate: formatLocalDate(date),
    noonType,
    noonLabel: NOON_LABELS[noonType] ?? '—',
  }
}

export function normalizeScheduleWorkDate(value) {
  if (!value) return ''
  if (typeof value === 'string') return value.slice(0, 10)
  return ''
}

/** 窗口挂号可见午别：上午含下午/晚上，下午含晚上，晚上仅晚上 */
export function isWindowNoonVisible(slotNoonType, currentNoonType) {
  return Number(slotNoonType) >= Number(currentNoonType)
}

/** 兜底过滤：即便后端仍返回 7 天号源，窗口页也只展示当天当前午别及以后 */
export function filterWindowSchedules(list, ctx, filters = {}) {
  const { employeeId, registLevelId } = filters
  return (list ?? []).filter((row) => {
    const rowDate = normalizeScheduleWorkDate(row.workDate)
    if (!rowDate || rowDate !== ctx.workDate) return false
    if (row.noonType == null || !isWindowNoonVisible(row.noonType, ctx.noonType)) return false
    if (employeeId != null && Number(row.employeeId) !== Number(employeeId)) return false
    if (registLevelId != null && Number(row.registLevelId) !== Number(registLevelId)) return false
    return true
  })
}
