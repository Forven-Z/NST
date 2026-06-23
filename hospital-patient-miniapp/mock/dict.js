/**
 * Mock 排班字典（与 PC mock/dict.js、seed-dict.sql 对齐，CommonJS）
 */
const OUTPATIENT_DEPTS = [
  { id: 1, deptName: '内科' },
  { id: 6, deptName: '外科' },
]

const DOCTORS = [
  { employeeId: 1, realName: '张医生', title: '主治医师', deptId: 1, role: 'regular' },
  { employeeId: 7, realName: '李医生', title: '主治医师', deptId: 1, role: 'regular' },
  { employeeId: 8, realName: '陈教授', title: '主任医师', deptId: 1, role: 'expert' },
  { employeeId: 9, realName: '王医生', title: '主治医师', deptId: 6, role: 'regular' },
  { employeeId: 10, realName: '刘教授', title: '主任医师', deptId: 6, role: 'expert' },
  { employeeId: 12, realName: '赵医生', title: '主治医师', deptId: 6, role: 'regular' },
]

const EXPERT_SLOTS = {
  8: [{ weekday: 1, noonType: 1 }, { weekday: 4, noonType: 1 }],
  10: [{ weekday: 2, noonType: 1 }, { weekday: 5, noonType: 1 }],
}

function formatDate(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function listInternalRegularDoctors(weekday) {
  const docs = []
  if (weekday !== 0) docs.push(DOCTORS.find((d) => d.employeeId === 1))
  if (weekday !== 3) docs.push(DOCTORS.find((d) => d.employeeId === 7))
  return docs.filter(Boolean)
}

function listSurgeryRegularDoctors(weekday) {
  const docs = []
  if (weekday !== 0) docs.push(DOCTORS.find((d) => d.employeeId === 9))
  if (weekday !== 3) docs.push(DOCTORS.find((d) => d.employeeId === 12))
  return docs.filter(Boolean)
}

function listRegularDoctors(deptId, weekday) {
  if (deptId === 1) return listInternalRegularDoctors(weekday)
  if (deptId === 6) return listSurgeryRegularDoctors(weekday)
  return []
}

function hasExpertSlot(employeeId, weekday, noonType) {
  const slots = EXPERT_SLOTS[employeeId] || []
  return slots.some((s) => s.weekday === weekday && s.noonType === noonType)
}

function buildSchedules() {
  const list = []
  let id = 5001
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  for (let off = 0; off < 7; off += 1) {
    const d = new Date(today)
    d.setDate(d.getDate() + off)
    const weekday = d.getDay()
    const workDate = formatDate(d)
    for (const dept of OUTPATIENT_DEPTS) {
      const experts = DOCTORS.filter((x) => x.deptId === dept.id && x.role === 'expert')
      for (const noon of [{ noonType: 1, noonLabel: '上午' }, { noonType: 2, noonLabel: '下午' }]) {
        const regDocs = listRegularDoctors(dept.id, weekday)
        for (const reg of regDocs) {
          list.push({
            schedulingId: id++,
            deptId: dept.id,
            deptName: dept.deptName,
            employeeId: reg.employeeId,
            doctorName: reg.realName,
            employeeTitle: reg.title,
            registLevelId: 1,
            levelName: '普通号',
            registFee: 20,
            workDate,
            noonType: noon.noonType,
            noonLabel: noon.noonLabel,
            remainQuota: 35 - (id % 5),
          })
        }
        for (const exp of experts) {
          if (!hasExpertSlot(exp.employeeId, weekday, noon.noonType)) continue
          list.push({
            schedulingId: id++,
            deptId: dept.id,
            deptName: dept.deptName,
            employeeId: exp.employeeId,
            doctorName: exp.realName,
            employeeTitle: exp.title,
            registLevelId: 2,
            levelName: '专家号',
            registFee: 65,
            workDate,
            noonType: noon.noonType,
            noonLabel: noon.noonLabel,
            remainQuota: 8,
          })
        }
      }
    }
  }
  return list
}

const SCHEDULES = buildSchedules()

function getSchedules(params) {
  const { deptId, workDate, noonType, registLevelId } = params || {}
  return SCHEDULES.filter((s) => {
    if (deptId && s.deptId !== Number(deptId)) return false
    if (workDate && s.workDate !== workDate) return false
    if (noonType && s.noonType !== Number(noonType)) return false
    if (registLevelId && s.registLevelId !== Number(registLevelId)) return false
    return s.remainQuota > 0
  })
}

function getScheduleById(id) {
  return SCHEDULES.find((s) => s.schedulingId === Number(id))
}

function consumeQuota(schedulingId) {
  const s = SCHEDULES.find((x) => x.schedulingId === Number(schedulingId))
  if (s && s.remainQuota > 0) s.remainQuota -= 1
}

module.exports = {
  OUTPATIENT_DEPTS,
  getSchedules,
  getScheduleById,
  consumeQuota,
}
