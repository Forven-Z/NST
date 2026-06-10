/**
 * Mock 字典与排班（门诊挂号）
 *
 * 排班规则依据公开资料归纳（见 mock/README.md §排班常识），非主观臆造：
 * - 普通号：各科室每个开诊半天均设普通门诊，住院/主治医师等轮流出诊
 * - 专家号：仅副主任医师及以上；多数固定每周 1～3 个半天，非每时段都有
 * - 开诊时段：常见上午 08:00–12:00、下午 13:00–17:00
 */

export const MOCK_SETTLE_CATEGORIES = [
  { id: 1, categoryCode: 'SELF_PAY', categoryName: '自费' },
  { id: 2, categoryCode: 'INSURANCE', categoryName: '医保' },
]

export const MOCK_REGIST_LEVELS = [
  { id: 1, levelCode: 'NORMAL', levelName: '普通号', registFee: 20 },
  { id: 2, levelCode: 'EXPERT', levelName: '专家号', registFee: 65 },
]

/** 门诊科室（Mock 扩展；seed 中已有内科等） */
export const MOCK_OUTPATIENT_DEPTS = [
  { id: 1, deptCode: 'INTERNAL', deptName: '内科', deptType: 1, sortNo: 1 },
  { id: 7, deptCode: 'SURGERY', deptName: '外科', deptType: 1, sortNo: 2 },
  { id: 8, deptCode: 'PEDIATRICS', deptName: '儿科', deptType: 1, sortNo: 3 },
  { id: 9, deptCode: 'OBSTETRICS', deptName: '妇产科', deptType: 1, sortNo: 4 },
]

/**
 * role:
 * - regular：仅出普通门诊（主治医师/住院医师等）
 * - expert：可出专家门诊（对应副高及以上职称）；普通门诊由 regular 医生承担
 */
export const MOCK_DOCTORS = [
  // 内科
  { employeeId: 1, empNo: 'E001', realName: '张医生', title: '主治医师', deptId: 1, role: 'regular' },
  { employeeId: 7, empNo: 'E007', realName: '刘医生', title: '住院医师', deptId: 1, role: 'regular' },
  { employeeId: 11, empNo: 'E011', realName: '王教授', title: '主任医师', deptId: 1, role: 'expert' },
  { employeeId: 12, empNo: 'E012', realName: '李主任', title: '副主任医师', deptId: 1, role: 'expert' },
  // 外科
  { employeeId: 8, empNo: 'E008', realName: '赵医生', title: '主治医师', deptId: 7, role: 'regular' },
  { employeeId: 13, empNo: 'E013', realName: '钱医生', title: '住院医师', deptId: 7, role: 'regular' },
  { employeeId: 14, empNo: 'E014', realName: '陈主任', title: '副主任医师', deptId: 7, role: 'expert' },
  // 儿科
  { employeeId: 9, empNo: 'E009', realName: '周医生', title: '主治医师', deptId: 8, role: 'regular' },
  { employeeId: 15, empNo: 'E015', realName: '孙医生', title: '住院医师', deptId: 8, role: 'regular' },
  { employeeId: 16, empNo: 'E016', realName: '郑主任', title: '副主任医师', deptId: 8, role: 'expert' },
  // 妇产科
  { employeeId: 10, empNo: 'E010', realName: '吴医生', title: '主治医师', deptId: 9, role: 'regular' },
  { employeeId: 17, empNo: 'E017', realName: '冯医生', title: '住院医师', deptId: 9, role: 'regular' },
  { employeeId: 18, empNo: 'E018', realName: '黄教授', title: '主任医师', deptId: 9, role: 'expert' },
]

/**
 * 专家出诊表：仅列出有专家门诊的半天（weekday: JS getDay() 0=周日…6=周六）
 * 参考：多数专家每周固定 1～3 天出诊，号源有限（百度健康·医学科普）
 */
const EXPERT_CLINIC_SLOTS = {
  11: [ // 王教授 内科：周一三五上午
    { weekday: 1, noonType: 1 },
    { weekday: 3, noonType: 1 },
    { weekday: 5, noonType: 1 },
  ],
  12: [ // 李主任 内科：周二四上午
    { weekday: 2, noonType: 1 },
    { weekday: 4, noonType: 1 },
  ],
  14: [ // 陈主任 外科：周三上午、周五下午
    { weekday: 3, noonType: 1 },
    { weekday: 5, noonType: 2 },
  ],
  16: [ // 郑主任 儿科：周二上午、周六上午
    { weekday: 2, noonType: 1 },
    { weekday: 6, noonType: 1 },
  ],
  18: [ // 黄教授 妇产科：周一下午、周四上午
    { weekday: 1, noonType: 2 },
    { weekday: 4, noonType: 1 },
  ],
}

const NOON_SESSIONS = [
  { noonType: 1, label: '上午', timeRange: '08:00-12:00' },
  { noonType: 2, label: '下午', timeRange: '13:00-17:00' },
]

/** 普通号默认号额；专家号号额更少（参考专家号需求大、号源紧） */
const NORMAL_QUOTA = 40
const EXPERT_QUOTA = 12

function formatDate(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function isClinicDay(weekday) {
  // 周日多数医院无日常门诊（以当日公告为准）；Mock 跳过周日
  return weekday !== 0
}

function isNormalSessionOpen(weekday, noonType) {
  if (!isClinicDay(weekday)) return false
  // 周六常仅开上午（参考多家医院「周六日出诊以公告为准」）
  if (weekday === 6 && noonType === 2) return false
  return true
}

function pickRegularDoctor(deptId, dayOffset, noonType, pool) {
  if (!pool.length) return null
  const idx = (deptId * 7 + dayOffset * 2 + noonType) % pool.length
  return pool[idx]
}

function hasExpertSlot(employeeId, weekday, noonType) {
  const slots = EXPERT_CLINIC_SLOTS[employeeId] || []
  return slots.some((s) => s.weekday === weekday && s.noonType === noonType)
}

/**
 * 非门诊出诊人员（医技、药房、挂号、处置）— Mock 值班排班
 * 与 auth.js 账号 employeeId 对齐
 */
export const MOCK_STAFF_MEMBERS = [
  { employeeId: 2, empNo: 'E002', realName: '李检验', title: '检验师', deptId: 3, roleType: 'LAB_DOCTOR' },
  { employeeId: 3, empNo: 'E003', realName: '王检查', title: '放射技师', deptId: 2, roleType: 'CHECK_DOCTOR' },
  { employeeId: 4, empNo: 'E004', realName: '赵药师', title: '主管药师', deptId: 4, roleType: 'PHARMACIST' },
  { employeeId: 5, empNo: 'E005', realName: '钱收费', title: '挂号收费员', deptId: 5, roleType: 'REGISTRAR' },
  { employeeId: 8, empNo: 'E008', realName: '周检验', title: '检验师', deptId: 3, roleType: 'LAB_DOCTOR' },
  { employeeId: 19, empNo: 'E019', realName: '孙处置', title: '护士', deptId: 6, roleType: 'DISPOSAL_DOCTOR' },
  { employeeId: 20, empNo: 'E020', realName: '吴检查', title: '放射技师', deptId: 2, roleType: 'CHECK_DOCTOR' },
  { employeeId: 21, empNo: 'E021', realName: '郑药师', title: '药师', deptId: 4, roleType: 'PHARMACIST' },
  { employeeId: 22, empNo: 'E022', realName: '冯收费', title: '挂号收费员', deptId: 5, roleType: 'REGISTRAR' },
]

const DUTY_SHIFT_NAMES = {
  2: '影像值班',
  3: '检验值班',
  4: '药房值班',
  5: '窗口值班',
  6: '处置值班',
}

function buildStaffDutySchedules(startId) {
  const list = []
  let schedulingId = startId
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  for (let dayOffset = 0; dayOffset < 7; dayOffset += 1) {
    const workDate = new Date(today)
    workDate.setDate(workDate.getDate() + dayOffset)
    const weekday = workDate.getDay()
    if (weekday === 0) continue

    for (const staff of MOCK_STAFF_MEMBERS) {
      const onMorning = (staff.employeeId + dayOffset) % 3 !== 2
      const onAfternoon = (staff.employeeId + dayOffset) % 4 === 0
      const sessions = []
      if (onMorning) sessions.push(NOON_SESSIONS[0])
      if (onAfternoon && weekday !== 6) sessions.push(NOON_SESSIONS[1])

      for (const noon of sessions) {
        list.push({
          schedulingId: schedulingId++,
          scheduleType: 'DUTY',
          scheduleKind: 2,
          deptId: staff.deptId,
          employeeId: staff.employeeId,
          employeeName: staff.realName,
          employeeTitle: staff.title,
          registLevelId: 0,
          registLevelName: DUTY_SHIFT_NAMES[staff.deptId] || '值班',
          registFee: 0,
          workDate: formatDate(workDate),
          weekday,
          noonType: noon.noonType,
          noonLabel: noon.label,
          timeRange: noon.timeRange,
          totalQuota: 0,
          usedQuota: 0,
          remainQuota: 0,
          publishStatus: 1,
          isRotating: true,
        })
      }
    }
  }
  return list
}

function buildSchedules() {
  const list = []
  let schedulingId = 5001
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  for (let dayOffset = 0; dayOffset < 7; dayOffset += 1) {
    const workDate = new Date(today)
    workDate.setDate(workDate.getDate() + dayOffset)
    const weekday = workDate.getDay()

    for (const dept of MOCK_OUTPATIENT_DEPTS) {
      const regularPool = MOCK_DOCTORS.filter((d) => d.deptId === dept.id && d.role === 'regular')
      const expertPool = MOCK_DOCTORS.filter((d) => d.deptId === dept.id && d.role === 'expert')

      for (const noon of NOON_SESSIONS) {
        if (!isNormalSessionOpen(weekday, noon.noonType)) continue

        // ① 每个开诊半天必有普通号，医生轮流（保障日常号源）
        const regDoc = pickRegularDoctor(dept.id, dayOffset, noon.noonType, regularPool)
        if (regDoc) {
          const used = dayOffset === 0 && noon.noonType === 1 ? 5 : (schedulingId % 4)
          list.push({
            schedulingId: schedulingId++,
            deptId: dept.id,
            employeeId: regDoc.employeeId,
            employeeName: regDoc.realName,
            employeeTitle: regDoc.title,
            registLevelId: 1,
            registLevelName: '普通号',
            registFee: 20,
            clinicType: 'NORMAL',
            workDate: formatDate(workDate),
            weekday,
            noonType: noon.noonType,
            noonLabel: noon.label,
            timeRange: noon.timeRange,
            totalQuota: NORMAL_QUOTA,
            usedQuota: Math.min(used, NORMAL_QUOTA - 1),
            remainQuota: NORMAL_QUOTA - Math.min(used, NORMAL_QUOTA - 1),
            publishStatus: 1,
            scheduleKind: 1,
            isRotating: true,
          })
        }

        // ② 专家号：仅专家出诊表中的半天
        for (const expDoc of expertPool) {
          if (!hasExpertSlot(expDoc.employeeId, weekday, noon.noonType)) continue
          const usedExpert = dayOffset === 0 && noon.noonType === 1 ? 8 : 2
          list.push({
            schedulingId: schedulingId++,
            deptId: dept.id,
            employeeId: expDoc.employeeId,
            employeeName: expDoc.realName,
            employeeTitle: expDoc.title,
            registLevelId: 2,
            registLevelName: '专家号',
            registFee: 65,
            clinicType: 'EXPERT',
            workDate: formatDate(workDate),
            weekday,
            noonType: noon.noonType,
            noonLabel: noon.label,
            timeRange: noon.timeRange,
            totalQuota: EXPERT_QUOTA,
            usedQuota: Math.min(usedExpert, EXPERT_QUOTA - 1),
            remainQuota: EXPERT_QUOTA - Math.min(usedExpert, EXPERT_QUOTA - 1),
            publishStatus: 1,
            scheduleKind: 1,
            isRotating: false,
          })
        }
      }
    }
  }

  const dutyList = buildStaffDutySchedules(schedulingId)
  const merged = [...list, ...dutyList]

  return merged.sort((a, b) => {
    if (a.workDate !== b.workDate) return a.workDate.localeCompare(b.workDate)
    if (a.noonType !== b.noonType) return a.noonType - b.noonType
    return (a.registLevelId || 0) - (b.registLevelId || 0)
  })
}

export const MOCK_SCHEDULES = buildSchedules()

export function getDoctorsByDept(deptId) {
  return MOCK_DOCTORS.filter((d) => d.deptId === deptId)
}

export function getSchedules(deptId, employeeId, registLevelId) {
  return MOCK_SCHEDULES.filter((s) => {
    if (s.deptId !== deptId) return false
    if (employeeId && s.employeeId !== employeeId) return false
    if (registLevelId && s.registLevelId !== registLevelId) return false
    return true
  })
}

export function getDeptById(deptId) {
  return MOCK_OUTPATIENT_DEPTS.find((d) => d.id === deptId)
}

export function getDoctorById(employeeId) {
  return MOCK_DOCTORS.find((d) => d.employeeId === employeeId)
}

export function getScheduleById(schedulingId) {
  return MOCK_SCHEDULES.find((s) => s.schedulingId === schedulingId)
}

/** 专家在本科室未来 7 天内的专家门诊半天数（用于 UI 提示） */
export function countExpertSessions(deptId, employeeId) {
  return MOCK_SCHEDULES.filter(
    (s) => s.deptId === deptId && s.employeeId === employeeId && s.clinicType === 'EXPERT',
  ).length
}

/** 医技/行政科室（与 seed-dict.sql 对齐） */
export const MOCK_TECH_DEPARTMENTS = [
  { id: 2, deptCode: 'RADIOLOGY', deptName: '放射科', deptType: 2, sortNo: 5 },
  { id: 3, deptCode: 'LAB', deptName: '检验科', deptType: 2, sortNo: 6 },
  { id: 4, deptCode: 'PHARMACY', deptName: '药房', deptType: 3, sortNo: 7 },
  { id: 5, deptCode: 'REGISTRATION', deptName: '挂号收费处', deptType: 4, sortNo: 8 },
  { id: 6, deptCode: 'DISPOSAL', deptName: '处置科', deptType: 2, sortNo: 9 },
  { id: 10, deptCode: 'SYS_ADMIN', deptName: '系统管理科', deptType: 4, sortNo: 10 },
]

export const MOCK_ALL_DEPARTMENTS = [...MOCK_OUTPATIENT_DEPTS, ...MOCK_TECH_DEPARTMENTS]

/** 医技项目（与 seed 一致） */
export const MOCK_MEDICAL_TECHNOLOGIES = [
  { id: 1, itemCode: 'CHK-CT-HEAD', itemName: '头部 CT', techType: 'CHECK', price: 280, deptId: 2 },
  { id: 2, itemCode: 'INS-BLOOD', itemName: '血常规', techType: 'INSPECTION', price: 35, deptId: 3 },
  { id: 3, itemCode: 'INS-GLU', itemName: '空腹血糖', techType: 'INSPECTION', price: 12, deptId: 3 },
  { id: 4, itemCode: 'CHK-CXR', itemName: '胸部 X 线', techType: 'CHECK', price: 90, deptId: 2 },
  { id: 5, itemCode: 'DIS-WASH', itemName: '洗胃', techType: 'DISPOSAL', price: 120, deptId: 6 },
  { id: 6, itemCode: 'DIS-INF', itemName: '静脉输液', techType: 'DISPOSAL', price: 45, deptId: 6 },
]

export const MOCK_DRUGS = [
  { id: 1, drugCode: 'DRG-001', drugName: '阿莫西林胶囊', drugFormat: '0.25g×24粒', drugDosage: '胶囊', drugType: '处方药', unit: '盒', retailPrice: 18.5, stockQty: 100 },
  { id: 2, drugCode: 'DRG-002', drugName: '布洛芬缓释胶囊', drugFormat: '0.3g×20粒', drugDosage: '胶囊', drugType: '处方药', unit: '盒', retailPrice: 22, stockQty: 80 },
  { id: 3, drugCode: 'DRG-003', drugName: '对乙酰氨基酚片', drugFormat: '0.5g×20片', drugDosage: '片剂', drugType: '处方药', unit: '盒', retailPrice: 8.5, stockQty: 200 },
]

export const MOCK_DISEASES = [
  { id: 101, diseaseCode: 'G43', diseaseName: '偏头痛' },
  { id: 102, diseaseCode: 'G44', diseaseName: '紧张性头痛' },
  { id: 103, diseaseCode: 'J06', diseaseName: '急性上呼吸道感染' },
  { id: 104, diseaseCode: 'R51', diseaseName: '头痛' },
]

export function getMedicalTechById(id) {
  return MOCK_MEDICAL_TECHNOLOGIES.find((t) => t.id === id)
}

export function getDrugById(id) {
  return MOCK_DRUGS.find((d) => d.id === id)
}

/** 窗口挂号成功后扣减号源 */
export function consumeScheduleQuota(schedulingId) {
  const row = MOCK_SCHEDULES.find((s) => s.schedulingId === schedulingId)
  if (!row || row.remainQuota <= 0) return false
  row.usedQuota += 1
  row.remainQuota -= 1
  return true
}
