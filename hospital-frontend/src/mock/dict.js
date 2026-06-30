/**
 * Mock 字典与排班（门诊挂号）
 *
 * 排班规则依据公开资料归纳（见 mock/README.md §排班常识），非主观臆造：
 * - 普通号：含周日；同一半天可多名医生同时出诊；每人固定休 1 天/周（上 6 天）
 * - 专家号：每周 2 个上午
 */
import { getWindowSessionContext, isWindowNoonVisible } from '../utils/window-session'

export const MOCK_SETTLE_CATEGORIES = [
  { id: 1, categoryCode: 'SELF_PAY', categoryName: '自费' },
  { id: 2, categoryCode: 'INSURANCE', categoryName: '医保' },
]

export const MOCK_REGIST_LEVELS = [
  { id: 1, levelCode: 'NORMAL', levelName: '普通号', registFee: 20 },
  { id: 2, levelCode: 'EXPERT', levelName: '专家号', registFee: 65 },
]

/** 门诊科室（与 seed-dict.sql 对齐：内科 id=1、外科 id=6） */
export const MOCK_OUTPATIENT_DEPTS = [
  { id: 1, deptCode: 'INTERNAL', deptName: '内科', deptType: 1, sortNo: 1 },
  { id: 6, deptCode: 'SURGERY', deptName: '外科', deptType: 1, sortNo: 6 },
]

/**
 * role:
 * - regular：仅出普通门诊
 * - expert：可出专家门诊（对应副高及以上职称）
 */
export const MOCK_DOCTORS = [
  { employeeId: 1, empNo: 'E001', realName: '张医生', title: '主治医师', deptId: 1, role: 'regular' },
  { employeeId: 7, empNo: 'E007', realName: '李医生', title: '主治医师', deptId: 1, role: 'regular' },
  { employeeId: 8, empNo: 'E008', realName: '陈教授', title: '主任医师', deptId: 1, role: 'expert' },
  { employeeId: 9, empNo: 'E009', realName: '王医生', title: '主治医师', deptId: 6, role: 'regular' },
  { employeeId: 10, empNo: 'E010', realName: '刘教授', title: '主任医师', deptId: 6, role: 'expert' },
  { employeeId: 12, empNo: 'E012', realName: '赵医生', title: '主治医师', deptId: 6, role: 'regular' },
]

/**
 * 专家出诊表：仅列出有专家门诊的半天（weekday: JS getDay() 0=周日…6=周六）
 * 参考：多数专家每周固定 1～3 天出诊，号源有限（百度健康·医学科普）
 */
const EXPERT_CLINIC_SLOTS = {
  8: [ // 陈教授 内科：周一、周四上午（每周 2 半天）
    { weekday: 1, noonType: 1 },
    { weekday: 4, noonType: 1 },
  ],
  10: [ // 刘教授 外科：周二、周五上午（每周 2 半天）
    { weekday: 2, noonType: 1 },
    { weekday: 5, noonType: 1 },
  ],
}

const NOON_SESSIONS = [
  { noonType: 1, label: '上午', timeRange: '08:00-12:00' },
  { noonType: 2, label: '下午', timeRange: '13:00-17:00' },
]

/** 普通号默认号额；专家号号额更少（参考专家号需求大、号源紧） */
const NORMAL_QUOTA = 40
const EXPERT_QUOTA = 12

export function formatDate(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function isNormalSessionOpen() {
  return true
}

/** 内科普通：张(1)休周日、李(7)休周三；其余日期两人同时在岗 */
function listInternalRegularDoctors(weekday) {
  const docs = []
  if (weekday !== 0) docs.push(MOCK_DOCTORS.find((d) => d.employeeId === 1))
  if (weekday !== 3) docs.push(MOCK_DOCTORS.find((d) => d.employeeId === 7))
  return docs.filter(Boolean)
}

/** 外科普通：王(9)休周日、赵(12)休周三；其余日期两人同时在岗 */
function listSurgeryRegularDoctors(weekday) {
  const docs = []
  if (weekday !== 0) docs.push(MOCK_DOCTORS.find((d) => d.employeeId === 9))
  if (weekday !== 3) docs.push(MOCK_DOCTORS.find((d) => d.employeeId === 12))
  return docs.filter(Boolean)
}

function listRegularDoctors(deptId, dayOffset, noonType, pool, weekday) {
  if (deptId === 1) return listInternalRegularDoctors(weekday)
  if (deptId === 6) return listSurgeryRegularDoctors()
  if (!pool.length) return []
  const idx = (deptId * 7 + dayOffset * 2 + noonType) % pool.length
  return [pool[idx]]
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
  { employeeId: 3, empNo: 'E003', realName: '王检查', title: '影像医师', deptId: 2, roleType: 'CHECK_DOCTOR' },
  { employeeId: 4, empNo: 'E004', realName: '赵药师', title: '主管药师', deptId: 4, roleType: 'PHARMACIST' },
  { employeeId: 5, empNo: 'E005', realName: '钱收费', title: '挂号收费员', deptId: 5, roleType: 'REGISTRAR' },
  { employeeId: 11, empNo: 'E011', realName: '孙处置', title: '处置医师', deptId: 7, roleType: 'DISPOSAL_DOCTOR' },
  { employeeId: 13, empNo: 'E013', realName: '周检验', title: '检验师', deptId: 3, roleType: 'LAB_DOCTOR' },
  { employeeId: 15, empNo: 'E015', realName: '李影像', title: '影像医师', deptId: 2, roleType: 'CHECK_DOCTOR' },
  { employeeId: 16, empNo: 'E016', realName: '陈影像', title: '影像医师', deptId: 2, roleType: 'CHECK_DOCTOR' },
]

const DUTY_SHIFT_NAMES = {
  2: '影像值班',
  3: '检验值班',
  4: '药房值班',
  5: '窗口值班',
  7: '处置值班',
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

    for (const staff of MOCK_STAFF_MEMBERS) {
      const onMorning = (staff.employeeId + dayOffset) % 3 !== 2
      const onAfternoon = (staff.employeeId + dayOffset) % 4 === 0
      const sessions = []
      if (onMorning) sessions.push(NOON_SESSIONS[0])
      if (onAfternoon) sessions.push(NOON_SESSIONS[1])

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
        if (!isNormalSessionOpen()) continue

        // ① 每个开诊半天：所有在岗普通医生各一条排班（可多人在岗）
        const regDocs = listRegularDoctors(dept.id, dayOffset, noon.noonType, regularPool, weekday)
        for (const regDoc of regDocs) {
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
            isRotating: false,
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

export function resolveCurrentNoonType(date = new Date()) {
  const h = date.getHours()
  if (h < 13) return 1
  if (h < 18) return 2
  return 3
}

const NOON_LABEL = { 1: '上午', 2: '下午', 3: '晚上' }

export function resolveCurrentNoonLabel(date = new Date()) {
  return NOON_LABEL[resolveCurrentNoonType(date)] ?? '—'
}

export function getSchedules(deptId, employeeId, registLevelId, workDate, noonType) {
  const ctx = getWindowSessionContext()
  const targetDate = workDate || ctx.workDate
  const targetNoon = noonType ?? ctx.noonType
  const dept = Number(deptId)
  return MOCK_SCHEDULES.filter((s) => {
    if (Number(s.deptId) !== dept) return false
    if (s.workDate !== targetDate) return false
    if (!isWindowNoonVisible(s.noonType, targetNoon)) return false
    if (employeeId != null && Number(s.employeeId) !== Number(employeeId)) return false
    if (registLevelId != null && Number(s.registLevelId) !== Number(registLevelId)) return false
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
  { id: 2, deptCode: 'RADIOLOGY', deptName: '放射科', deptType: 2, sortNo: 2 },
  { id: 3, deptCode: 'LAB', deptName: '检验科', deptType: 2, sortNo: 3 },
  { id: 4, deptCode: 'PHARMACY', deptName: '药房', deptType: 3, sortNo: 4 },
  { id: 5, deptCode: 'REGISTRATION', deptName: '挂号收费处', deptType: 4, sortNo: 5 },
  { id: 7, deptCode: 'DISPOSAL', deptName: '处置科', deptType: 2, sortNo: 7 },
  { id: 8, deptCode: 'INFO_CENTER', deptName: '信息科', deptType: 4, sortNo: 8 },
]

export const MOCK_ALL_DEPARTMENTS = [...MOCK_OUTPATIENT_DEPTS, ...MOCK_TECH_DEPARTMENTS]

/** 医技项目（与 seed-dict.sql / RAG TECHNOLOGY_GUIDE 对齐） */
export const MOCK_MEDICAL_TECHNOLOGIES = [
  { id: 1, itemCode: 'CHK-CT-HEAD', itemName: '头部 CT', techType: 'CHECK', price: 280, deptId: 2 },
  { id: 2, itemCode: 'INS-BLOOD', itemName: '血常规', techType: 'INSPECTION', price: 35, deptId: 3 },
  { id: 3, itemCode: 'INS-CRP', itemName: 'C反应蛋白', techType: 'INSPECTION', price: 45, deptId: 3 },
  { id: 4, itemCode: 'INS-PCT', itemName: '降钙素原', techType: 'INSPECTION', price: 80, deptId: 3 },
  { id: 5, itemCode: 'DIS-WASH', itemName: '洗胃', techType: 'DISPOSAL', price: 120, deptId: 7 },
  { id: 6, itemCode: 'DIS-INF', itemName: '静脉输液', techType: 'DISPOSAL', price: 45, deptId: 7 },
  { id: 7, itemCode: 'CHK-CT-LUNG', itemName: '肺部 CT', techType: 'CHECK', price: 320, deptId: 2 },
  { id: 8, itemCode: 'CHK-TUMOR-SEG', itemName: '肿瘤 CT 分割', techType: 'CHECK', price: 450, deptId: 2 },
  { id: 9, itemCode: 'INS-URINE', itemName: '尿常规', techType: 'INSPECTION', price: 25, deptId: 3 },
  { id: 10, itemCode: 'INS-STOOL', itemName: '粪便常规及隐血', techType: 'INSPECTION', price: 30, deptId: 3 },
  { id: 11, itemCode: 'INS-LIVER', itemName: '肝功能', techType: 'INSPECTION', price: 55, deptId: 3 },
  { id: 12, itemCode: 'INS-KIDNEY', itemName: '肾功能', techType: 'INSPECTION', price: 50, deptId: 3 },
  { id: 13, itemCode: 'INS-GLU', itemName: '空腹血糖', techType: 'INSPECTION', price: 12, deptId: 3 },
  { id: 14, itemCode: 'INS-HBA1C', itemName: '糖化血红蛋白', techType: 'INSPECTION', price: 60, deptId: 3 },
  { id: 15, itemCode: 'INS-LIPID', itemName: '血脂四项', techType: 'INSPECTION', price: 70, deptId: 3 },
  { id: 16, itemCode: 'INS-ELECTROLYTE', itemName: '电解质', techType: 'INSPECTION', price: 40, deptId: 3 },
  { id: 17, itemCode: 'INS-COAG', itemName: '凝血功能', techType: 'INSPECTION', price: 65, deptId: 3 },
  { id: 18, itemCode: 'INS-THYROID', itemName: '甲状腺功能', techType: 'INSPECTION', price: 90, deptId: 3 },
  { id: 19, itemCode: 'INS-CARDIAC', itemName: '心肌标志物', techType: 'INSPECTION', price: 120, deptId: 3 },
  { id: 20, itemCode: 'INS-RESP-AG', itemName: '呼吸道病原抗原', techType: 'INSPECTION', price: 85, deptId: 3 },
  { id: 21, itemCode: 'CHK-CXR', itemName: '胸部 X 线', techType: 'CHECK', price: 90, deptId: 2 },
  { id: 22, itemCode: 'CHK-MRI-BRAIN', itemName: '颅脑 MRI', techType: 'CHECK', price: 680, deptId: 2 },
  { id: 23, itemCode: 'CHK-CT-ABD', itemName: '腹部 CT', techType: 'CHECK', price: 350, deptId: 2 },
  { id: 24, itemCode: 'CHK-US-ABD', itemName: '腹部超声', techType: 'CHECK', price: 120, deptId: 2 },
  { id: 25, itemCode: 'CHK-US-THYROID', itemName: '甲状腺超声', techType: 'CHECK', price: 100, deptId: 2 },
  { id: 26, itemCode: 'CHK-US-URINARY', itemName: '泌尿系统超声', techType: 'CHECK', price: 110, deptId: 2 },
  { id: 27, itemCode: 'CHK-ECG', itemName: '十二导联心电图', techType: 'CHECK', price: 30, deptId: 2 },
  { id: 28, itemCode: 'CHK-ECHO', itemName: '超声心动图', techType: 'CHECK', price: 180, deptId: 2 },
  { id: 29, itemCode: 'CHK-HOLTER', itemName: '动态心电图', techType: 'CHECK', price: 200, deptId: 2 },
  { id: 30, itemCode: 'CHK-PFT', itemName: '肺功能检查', techType: 'CHECK', price: 150, deptId: 2 },
  { id: 31, itemCode: 'DIS-DRESSING', itemName: '清创换药', techType: 'DISPOSAL', price: 80, deptId: 7 },
  { id: 32, itemCode: 'DIS-NEB', itemName: '雾化吸入', techType: 'DISPOSAL', price: 35, deptId: 7 },
  { id: 33, itemCode: 'DIS-O2', itemName: '氧疗', techType: 'DISPOSAL', price: 50, deptId: 7 },
  { id: 34, itemCode: 'DIS-CATH', itemName: '导尿', techType: 'DISPOSAL', price: 40, deptId: 7 },
]

/** 药品（与 seed / RAG DRUG-001～020 对齐） */
export const MOCK_DRUGS = [
  { id: 1, drugCode: 'DRG-001', drugName: '阿莫西林胶囊', drugFormat: '0.25g×24粒', drugDosage: '胶囊', drugType: '处方药', unit: '盒', retailPrice: 18.5, stockQty: 100 },
  { id: 2, drugCode: 'DRG-002', drugName: '布洛芬缓释胶囊', drugFormat: '0.3g×20粒', drugDosage: '胶囊', drugType: '处方药', unit: '盒', retailPrice: 22, stockQty: 80 },
  { id: 3, drugCode: 'DRG-003', drugName: '对乙酰氨基酚片', drugFormat: '0.5g×20片', drugDosage: '片剂', drugType: '处方药', unit: '盒', retailPrice: 8.5, stockQty: 200 },
  { id: 4, drugCode: 'DRG-004', drugName: '氯雷他定片', drugFormat: '10mg×6片', drugDosage: '片剂', drugType: '处方药', unit: '盒', retailPrice: 16, stockQty: 120 },
  { id: 5, drugCode: 'DRG-005', drugName: '盐酸西替利嗪片', drugFormat: '10mg×12片', drugDosage: '片剂', drugType: '处方药', unit: '盒', retailPrice: 14.5, stockQty: 100 },
  { id: 6, drugCode: 'DRG-006', drugName: '盐酸氨溴索片', drugFormat: '30mg×20片', drugDosage: '片剂', drugType: '处方药', unit: '盒', retailPrice: 19, stockQty: 90 },
  { id: 7, drugCode: 'DRG-007', drugName: '乙酰半胱氨酸颗粒', drugFormat: '0.1g×10袋', drugDosage: '颗粒', drugType: '处方药', unit: '盒', retailPrice: 28, stockQty: 60 },
  { id: 8, drugCode: 'DRG-008', drugName: '奥美拉唑肠溶胶囊', drugFormat: '20mg×14粒', drugDosage: '胶囊', drugType: '处方药', unit: '盒', retailPrice: 25, stockQty: 80 },
  { id: 9, drugCode: 'DRG-009', drugName: '蒙脱石散', drugFormat: '3g×10袋', drugDosage: '散剂', drugType: 'OTC', unit: '盒', retailPrice: 12, stockQty: 150 },
  { id: 10, drugCode: 'DRG-010', drugName: '口服补液盐散', drugFormat: '20.5g×3袋', drugDosage: '散剂', drugType: 'OTC', unit: '盒', retailPrice: 15, stockQty: 100 },
  { id: 11, drugCode: 'DRG-011', drugName: '二甲双胍片', drugFormat: '0.5g×48片', drugDosage: '片剂', drugType: '处方药', unit: '盒', retailPrice: 11, stockQty: 120 },
  { id: 12, drugCode: 'DRG-012', drugName: '苯磺酸氨氯地平片', drugFormat: '5mg×7片', drugDosage: '片剂', drugType: '处方药', unit: '盒', retailPrice: 18, stockQty: 100 },
  { id: 13, drugCode: 'DRG-013', drugName: '氯沙坦钾片', drugFormat: '50mg×7片', drugDosage: '片剂', drugType: '处方药', unit: '盒', retailPrice: 32, stockQty: 80 },
  { id: 14, drugCode: 'DRG-014', drugName: '阿托伐他汀钙片', drugFormat: '20mg×7片', drugDosage: '片剂', drugType: '处方药', unit: '盒', retailPrice: 38, stockQty: 70 },
  { id: 15, drugCode: 'DRG-015', drugName: '硫酸沙丁胺醇吸入气雾剂', drugFormat: '100μg×200揿', drugDosage: '气雾剂', drugType: '处方药', unit: '瓶', retailPrice: 42, stockQty: 50 },
  { id: 16, drugCode: 'DRG-016', drugName: '吸入用布地奈德混悬液', drugFormat: '1mg×5支', drugDosage: '混悬液', drugType: '处方药', unit: '盒', retailPrice: 68, stockQty: 40 },
  { id: 17, drugCode: 'DRG-017', drugName: '阿奇霉素片', drugFormat: '0.25g×6片', drugDosage: '片剂', drugType: '处方药', unit: '盒', retailPrice: 24, stockQty: 90 },
  { id: 18, drugCode: 'DRG-018', drugName: '头孢呋辛酯片', drugFormat: '0.25g×12片', drugDosage: '片剂', drugType: '处方药', unit: '盒', retailPrice: 26.5, stockQty: 85 },
  { id: 19, drugCode: 'DRG-019', drugName: '莫匹罗星软膏', drugFormat: '2% 5g', drugDosage: '软膏', drugType: 'OTC', unit: '支', retailPrice: 22, stockQty: 60 },
  { id: 20, drugCode: 'DRG-020', drugName: '复方氨酚烷胺片', drugFormat: '12片', drugDosage: '片剂', drugType: 'OTC', unit: '盒', retailPrice: 9.5, stockQty: 180 },
]

export const MOCK_DISEASES = [
  { id: 1, diseaseCode: 'J06.9', diseaseName: '急性上呼吸道感染', diseaseCategory: '呼吸系统' },
  { id: 2, diseaseCode: 'I10', diseaseName: '原发性高血压', diseaseCategory: '循环系统' },
  { id: 3, diseaseCode: 'R51', diseaseName: '头痛', diseaseCategory: '神经系统' },
  { id: 4, diseaseCode: 'R50.9', diseaseName: '发热', diseaseCategory: '症状体征' },
  { id: 5, diseaseCode: 'E11.9', diseaseName: '2型糖尿病', diseaseCategory: '内分泌' },
]

export function getMedicalTechById(id) {
  return MOCK_MEDICAL_TECHNOLOGIES.find((t) => t.id === id)
}

export function getDrugById(id) {
  return MOCK_DRUGS.find((d) => d.id === id && !d.disabled)
}

function nextMockDrugCode() {
  const nums = MOCK_DRUGS.map((d) => {
    const m = d.drugCode?.match(/^DRG-(\d+)$/)
    return m ? parseInt(m[1], 10) : 0
  })
  const next = (nums.length ? Math.max(...nums) : 0) + 1
  return `DRG-${String(next).padStart(3, '0')}`
}

function nextMockDrugId() {
  return MOCK_DRUGS.reduce((max, d) => Math.max(max, d.id), 0) + 1
}

export function getPharmacyDrugList(keyword, includeDisabled = false) {
  const kw = keyword?.trim().toLowerCase()
  return MOCK_DRUGS.filter((d) => {
    if (!includeDisabled && d.disabled) return false
    if (!kw) return true
    return (
      d.drugName?.toLowerCase().includes(kw) ||
      d.drugCode?.toLowerCase().includes(kw)
    )
  }).map((d) => ({ ...d, disabled: !!d.disabled }))
}

export function createPharmacyDrug(body) {
  const drug = {
    id: nextMockDrugId(),
    drugCode: nextMockDrugCode(),
    drugName: body.drugName?.trim(),
    drugFormat: body.drugFormat || null,
    drugDosage: body.drugDosage || null,
    drugType: body.drugType || null,
    unit: body.unit || null,
    retailPrice: body.retailPrice,
    stockQty: body.stockQty,
    disabled: false,
  }
  MOCK_DRUGS.push(drug)
  return { ...drug }
}

export function updatePharmacyDrug(id, body) {
  const drug = MOCK_DRUGS.find((d) => d.id === id)
  if (!drug) throw new Error('药品不存在')
  if (body.drugName != null) drug.drugName = body.drugName.trim()
  if (body.drugFormat != null) drug.drugFormat = body.drugFormat || null
  if (body.drugDosage != null) drug.drugDosage = body.drugDosage || null
  if (body.drugType != null) drug.drugType = body.drugType || null
  if (body.unit != null) drug.unit = body.unit || null
  if (body.retailPrice != null) drug.retailPrice = body.retailPrice
  if (body.stockQty != null) drug.stockQty = body.stockQty
  return { ...drug, disabled: !!drug.disabled }
}

export function setPharmacyDrugDisabled(id, disabled) {
  const drug = MOCK_DRUGS.find((d) => d.id === id)
  if (!drug) throw new Error('药品不存在')
  if (disabled && drug.disabled) throw new Error('药品已停用')
  if (!disabled && !drug.disabled) throw new Error('药品未停用')
  drug.disabled = disabled
  return { ...drug, disabled: !!drug.disabled }
}

/** 窗口挂号成功后扣减号源 */
export function consumeScheduleQuota(schedulingId) {
  const row = MOCK_SCHEDULES.find((s) => s.schedulingId === schedulingId)
  if (!row || row.remainQuota <= 0) return false
  row.usedQuota += 1
  row.remainQuota -= 1
  return true
}
