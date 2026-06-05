/**
 * Mock 排班字典（与 PC mock/dict.js 规则一致，CommonJS）
 */
const OUTPATIENT_DEPTS = [
  { id: 1, deptName: '内科' },
  { id: 7, deptName: '外科' },
  { id: 8, deptName: '儿科' },
  { id: 9, deptName: '妇产科' },
]

const DOCTORS = [
  { employeeId: 1, realName: '张医生', title: '主治医师', deptId: 1, role: 'regular' },
  { employeeId: 7, realName: '刘医生', title: '住院医师', deptId: 1, role: 'regular' },
  { employeeId: 11, realName: '王教授', title: '主任医师', deptId: 1, role: 'expert' },
  { employeeId: 12, realName: '李主任', title: '副主任医师', deptId: 1, role: 'expert' },
  { employeeId: 8, realName: '赵医生', title: '主治医师', deptId: 7, role: 'regular' },
  { employeeId: 13, realName: '钱医生', title: '住院医师', deptId: 7, role: 'regular' },
  { employeeId: 14, realName: '陈主任', title: '副主任医师', deptId: 7, role: 'expert' },
  { employeeId: 9, realName: '周医生', title: '主治医师', deptId: 8, role: 'regular' },
  { employeeId: 16, realName: '郑主任', title: '副主任医师', deptId: 8, role: 'expert' },
  { employeeId: 10, realName: '吴医生', title: '主治医师', deptId: 9, role: 'regular' },
  { employeeId: 18, realName: '黄教授', title: '主任医师', deptId: 9, role: 'expert' },
]

const EXPERT_SLOTS = {
  11: [{ weekday: 1, noonType: 1 }, { weekday: 3, noonType: 1 }, { weekday: 5, noonType: 1 }],
  12: [{ weekday: 2, noonType: 1 }, { weekday: 4, noonType: 1 }],
  14: [{ weekday: 3, noonType: 1 }, { weekday: 5, noonType: 2 }],
  16: [{ weekday: 2, noonType: 1 }, { weekday: 6, noonType: 1 }],
  18: [{ weekday: 1, noonType: 2 }, { weekday: 4, noonType: 1 }],
}

function buildSchedules() {
  const list = []
  let id = 5001
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  for (let off = 0; off < 7; off += 1) {
    const d = new Date(today)
    d.setDate(d.getDate() + off)
    const wd = d.getDay()
    if (wd === 0) continue
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const workDate = `${y}-${m}-${day}`
    for (const dept of OUTPATIENT_DEPTS) {
      const regulars = DOCTORS.filter((x) => x.deptId === dept.id && x.role === 'regular')
      const experts = DOCTORS.filter((x) => x.deptId === dept.id && x.role === 'expert')
      for (const noon of [{ noonType: 1, noonLabel: '上午' }, { noonType: 2, noonLabel: '下午' }]) {
        if (wd === 6 && noon.noonType === 2) continue
        const reg = regulars[(off + dept.id + noon.noonType) % regulars.length]
        if (reg) {
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
          const slots = EXPERT_SLOTS[exp.employeeId] || []
          if (slots.some((s) => s.weekday === wd && s.noonType === noon.noonType)) {
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
