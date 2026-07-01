import { mockResult } from '../utils/mock'
import { mockAiSchedulingSuggestions } from './ai-reports'
import { MOCK_REGIST_LEVELS, MOCK_SCHEDULES } from './dict'
import { getDepartmentById, getEmployeeById, listEmployees } from './staff-registry'
import {
  getApprovedLeaveSchedulingIds,
  getPendingLeaveCount,
  markLeaveSubstituted,
  mockSubstituteProposal,
} from './scheduling-leave'

function isExpertDoctor(doctor) {
  const title = doctor?.title || ''
  return title.includes('主任医师') || title.includes('副主任医师') || title.includes('教授')
}

function buildLeaveAwareSuggestions(list) {
  const approvedIds = new Set(getApprovedLeaveSchedulingIds())
  const suggestions = []

  for (const s of list) {
    if (approvedIds.has(s.schedulingId) || s.substituteRequired || s.leaveApproved) {
      const proposed = mockSubstituteProposal(s)
      if (proposed) {
        suggestions.push({
          schedulingId: s.schedulingId,
          workDate: s.workDate,
          noonLabel: s.noonLabel,
          employeeName: s.employeeName,
          suggestion: `【请假替班】${s.employeeName} 已批准请假，建议由 ${proposed.employeeName}（${proposed.employeeTitle}）顶替该班次`,
          confidence: 0.91,
          replaceable: true,
          leaveDriven: true,
          proposedSchedule: proposed,
        })
        continue
      }
    }
  }

  const generic = mockAiSchedulingSuggestions(list.filter((s) => !approvedIds.has(s.schedulingId))).slice(0, 3)
  return [...suggestions, ...generic]
}

export function mockAiSchedulingSuggest(params) {
  if ((params?.mode || 'WEEK').toUpperCase() === 'SUBSTITUTE') {
    return mockAiSubstituteSuggest(params)
  }
  return mockAiWeekSuggest(params)
}

function mockAiSubstituteSuggest(params) {
  let list = [...MOCK_SCHEDULES].filter((s) => (s.publishStatus ?? 1) !== 2)
  if (params?.deptId) list = list.filter((s) => s.deptId === Number(params.deptId))
  const pendingLeave = getPendingLeaveCount()
  return mockResult({
    stub: true,
    mode: 'SUBSTITUTE',
    generatedAt: new Date().toISOString(),
    changes: [],
    suggestions: buildLeaveAwareSuggestions(list),
    pendingLeaveCount: pendingLeave,
    message: pendingLeave
      ? `检测到 ${pendingLeave} 条待审批请假；已优先生成替班建议`
      : '暂无需要替班的已批准请假',
  })
}

function mockAiWeekSuggest(params) {
  const deptId = Number(params?.deptId)
  const weekStart = alignMondayIso(params?.weekStart || new Date().toISOString().slice(0, 10))
  const weekEnd = addDaysIso(weekStart, 6)
  const doctors = listEmployees({ deptId, roleType: 'OUTPATIENT_DOCTOR', delmark: 0 })
  const regularDoctors = doctors.filter((d) => !isExpertDoctor(d))
  const expertDoctors = doctors.filter(isExpertDoctor)
  const existing = weekSlots(deptId, weekStart)
  const occupied = new Set(existing.map((s) => `${s.employeeId}|${s.workDate}|${s.noonType}`))
  const changes = []
  const warnings = []

  regularDoctors.forEach((doctor, index) => {
    const restDay = index % 7
    for (let day = 0; day < 7; day += 1) {
      if (day === restDay) continue
      for (const noonType of [1, 2]) {
        addAiChange(changes, occupied, doctor, addDaysIso(weekStart, day), noonType, 1, defaultQuota(1))
      }
    }
  })

  expertDoctors.forEach((doctor, index) => {
    const start = index * 3
    for (let session = 0; session < 3; session += 1) {
      const slotIndex = (start + session * 5) % 14
      addAiChange(
        changes,
        occupied,
        doctor,
        addDaysIso(weekStart, Math.floor(slotIndex / 2)),
        slotIndex % 2 === 0 ? 1 : 2,
        2,
        defaultQuota(2),
      )
    }
  })

  for (let day = 0; day < 7; day += 1) {
    const workDate = addDaysIso(weekStart, day)
    for (const noonType of [1, 2]) {
      const covered = existing.some((s) => s.workDate === workDate && s.noonType === noonType)
        || changes.some((c) => c.workDate === workDate && c.noonType === noonType)
      if (covered) continue
      const filler = [...regularDoctors, ...expertDoctors]
        .find((d) => !occupied.has(`${d.employeeId}|${workDate}|${noonType}`))
      if (filler) {
        const levelId = isExpertDoctor(filler) ? 2 : 1
        addAiChange(changes, occupied, filler, workDate, noonType, levelId, defaultQuota(levelId))
        warnings.push(`${workDate} ${noonType === 1 ? '上午' : '下午'} 原本无医生覆盖，已自动补充 ${filler.realName}`)
      } else {
        warnings.push(`${workDate} ${noonType === 1 ? '上午' : '下午'} 无可用医生补位，请手工安排`)
      }
    }
  }

  return mockResult({
    stub: true,
    mode: 'WEEK',
    weekStart,
    weekEnd,
    changes,
    suggestions: [],
    riskItems: [],
    warnings,
    message: `AI 已生成 ${changes.length} 条周排班草稿，请检查后保存`,
  })
}

function addAiChange(changes, occupied, doctor, workDate, noonType, registLevelId, totalQuota) {
  const key = `${doctor.employeeId}|${workDate}|${noonType}`
  if (occupied.has(key)) return
  changes.push({
    employeeId: doctor.employeeId,
    workDate,
    noonType,
    registLevelId,
    totalQuota,
  })
  occupied.add(key)
}
export function mockUpdateAdminSchedule(schedulingId, data) {
  const row = MOCK_SCHEDULES.find((s) => s.schedulingId === Number(schedulingId))
  if (!row) throw new Error('排班记录不存在')
  const used = row.usedQuota ?? Math.max(0, row.totalQuota - row.remainQuota)
  const totalQuota = Number(data.totalQuota ?? row.totalQuota)
  const remainQuota = Math.min(Number(data.remainQuota ?? row.remainQuota), totalQuota)
  const patch = {
    timeRange: data.timeRange ?? row.timeRange,
    totalQuota,
    remainQuota: data.aiOptimized || data.leaveSubstitute
      ? Math.max(0, totalQuota - used)
      : remainQuota,
    usedQuota: used,
    registFee: Number(data.registFee ?? row.registFee),
    manualEdited: !data.aiOptimized && !data.leaveSubstitute,
    aiApplied: !!(data.aiOptimized || data.leaveSubstitute),
    substituteRequired: false,
    leaveApproved: false,
  }
  if (data.employeeId) {
    patch.employeeId = Number(data.employeeId)
    patch.employeeName = data.employeeName ?? row.employeeName
    patch.employeeTitle = data.employeeTitle ?? row.employeeTitle
  } else if (data.employeeName) {
    patch.employeeName = data.employeeName
  }
  Object.assign(row, patch)
  if (patch.employeeId) {
    markLeaveSubstituted(schedulingId, patch.employeeId, patch.employeeName)
  }
  const msg = data.leaveSubstitute
    ? `已应用替班：${row.employeeName} 顶替 ${data.substituteFor || '原医生'}`
    : data.aiOptimized
      ? '已应用 AI 推荐排班'
      : data.employeeId
        ? `排班已更新，替班：${row.employeeName}`
        : '排班已手工更新'
  return mockResult({ schedulingId: row.schedulingId, ...row, message: msg })
}

let nextScheduleId = 90001

const NOON_META = {
  1: { label: '上午', timeRange: '08:00-12:00' },
  2: { label: '下午', timeRange: '13:00-17:00' },
  3: { label: '晚上', timeRange: '18:00-21:00' },
}

export function mockCreateAdminSchedule(data) {
  if (!data.employeeId) throw new Error('请选择出诊/值班人员')
  if (!data.workDate) throw new Error('请选择日期')
  const emp = getEmployeeById(data.employeeId)
  if (!emp || emp.delmark) throw new Error('所选员工不存在或已停用')
  const dept = getDepartmentById(emp.deptId)
  const level = MOCK_REGIST_LEVELS.find((l) => l.id === Number(data.registLevelId ?? 1))
  const noon = NOON_META[data.noonType ?? 1] || NOON_META[1]
  const scheduleKind = data.scheduleKind ?? 1
  nextScheduleId += 1
  const row = {
    schedulingId: nextScheduleId,
    scheduleKind,
    scheduleType: scheduleKind === 2 ? 'DUTY' : 'CLINIC',
    deptId: emp.deptId,
    deptName: dept?.deptName,
    employeeId: emp.employeeId,
    employeeName: emp.realName,
    employeeTitle: emp.title,
    registLevelId: scheduleKind === 2 ? null : (data.registLevelId ?? 1),
    registLevelName: scheduleKind === 2 ? (data.registLevelName || '值班') : (level?.levelName || '普通号'),
    workDate: data.workDate,
    noonType: data.noonType ?? 1,
    noonLabel: noon.label,
    timeRange: data.timeRange || noon.timeRange,
    totalQuota: data.totalQuota ?? (scheduleKind === 2 ? 0 : 20),
    usedQuota: 0,
    remainQuota: data.totalQuota ?? (scheduleKind === 2 ? 0 : 20),
    registFee: data.registFee ?? (level?.registFee ?? 20),
    publishStatus: 0,
  }
  MOCK_SCHEDULES.push(row)
  return mockResult({ ...row, message: '排班草稿已创建，请发布' })
}

export function mockPublishAdminSchedule(schedulingId) {
  const row = MOCK_SCHEDULES.find((s) => s.schedulingId === Number(schedulingId))
  if (!row) throw new Error('排班记录不存在')
  row.publishStatus = 1
  return mockResult({ ...row, message: '排班已发布，患者可挂号' })
}

const MOCK_TEMPLATES = new Map()

function alignMondayIso(dateStr) {
  const d = new Date(`${dateStr}T12:00:00`)
  const day = d.getDay()
  const diff = day === 0 ? -6 : 1 - day
  d.setDate(d.getDate() + diff)
  return d.toISOString().slice(0, 10)
}

function addDaysIso(dateStr, days) {
  const d = new Date(`${dateStr}T12:00:00`)
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

function defaultQuota(registLevelId) {
  return Number(registLevelId) === 2 ? 15 : 30
}

function weekSlots(deptId, weekStart) {
  const start = alignMondayIso(weekStart)
  const end = addDaysIso(start, 6)
  return MOCK_SCHEDULES.filter(
    (s) => s.deptId === Number(deptId)
      && s.workDate >= start
      && s.workDate <= end
      && (s.publishStatus ?? 1) !== 2,
  )
}

function mockDoctors(deptId) {
  const seen = new Map()
  for (const s of MOCK_SCHEDULES.filter((x) => x.deptId === Number(deptId))) {
    if (!seen.has(s.employeeId)) {
      seen.set(s.employeeId, {
        employeeId: s.employeeId,
        realName: s.employeeName,
        title: s.employeeTitle,
      })
    }
  }
  return [...seen.values()]
}

export function mockWeekGrid(params) {
  const deptId = Number(params.deptId)
  const weekStart = alignMondayIso(params.weekStart || new Date().toISOString().slice(0, 10))
  const weekEnd = addDaysIso(weekStart, 6)
  const slots = weekSlots(deptId, weekStart)
  const draftCount = slots.filter((s) => (s.publishStatus ?? 1) === 0).length
  const publishedCount = slots.filter((s) => (s.publishStatus ?? 1) === 1).length
  return mockResult({
    weekStart,
    weekEnd,
    deptId,
    doctors: mockDoctors(deptId),
    slots,
    prefilledFromTemplate: false,
    draftCount,
    publishedCount,
  })
}

export function mockBatchUpsertSchedules(data) {
  let created = 0
  let updated = 0
  let cleared = 0
  for (const change of data.changes || []) {
    if (change.clear) {
      const row = MOCK_SCHEDULES.find((s) => s.schedulingId === Number(change.schedulingId))
      if (row) {
        row.publishStatus = 2
        cleared += 1
      }
      continue
    }
    if (change.schedulingId) {
      const row = MOCK_SCHEDULES.find((s) => s.schedulingId === Number(change.schedulingId))
      if (row) {
        if (change.registLevelId != null) {
          row.registLevelId = change.registLevelId
          const level = MOCK_REGIST_LEVELS.find((l) => l.id === change.registLevelId)
          row.registLevelName = level?.levelName
        }
        if (change.totalQuota != null) {
          row.totalQuota = change.totalQuota
          row.remainQuota = Math.max(0, change.totalQuota - (row.usedQuota ?? 0))
        }
        updated += 1
      }
      continue
    }
    const emp = getEmployeeById(change.employeeId)
    const dept = getDepartmentById(emp?.deptId)
    const level = MOCK_REGIST_LEVELS.find((l) => l.id === Number(change.registLevelId ?? 1))
    const noon = NOON_META[change.noonType ?? 1] || NOON_META[1]
    nextScheduleId += 1
    const quota = change.totalQuota ?? defaultQuota(change.registLevelId)
    MOCK_SCHEDULES.push({
      schedulingId: nextScheduleId,
      deptId: emp.deptId,
      deptName: dept?.deptName,
      employeeId: change.employeeId,
      employeeName: emp.realName,
      employeeTitle: emp.title,
      registLevelId: change.registLevelId ?? 1,
      registLevelName: level?.levelName || '普通号',
      workDate: change.workDate,
      noonType: change.noonType,
      noonLabel: noon.label,
      timeRange: noon.timeRange,
      totalQuota: quota,
      usedQuota: 0,
      remainQuota: quota,
      registFee: level?.registFee ?? 20,
      publishStatus: 0,
    })
    created += 1
  }
  return mockResult({ created, updated, cleared, message: '排班已保存' })
}

export function mockCopyScheduleWeek(data) {
  const sourceStart = alignMondayIso(data.sourceWeekStart)
  const targetStart = alignMondayIso(data.targetWeekStart)
  const sourceSlots = weekSlots(data.deptId, sourceStart)
  let created = 0
  let skipped = 0
  for (const s of sourceSlots) {
    const srcDate = new Date(`${s.workDate}T12:00:00`)
    const offset = (srcDate.getDay() + 6) % 7
    const targetDate = addDaysIso(targetStart, offset)
    const exists = MOCK_SCHEDULES.some(
      (x) => x.employeeId === s.employeeId
        && x.workDate === targetDate
        && x.noonType === s.noonType
        && (x.publishStatus ?? 1) !== 2,
    )
    if (exists) {
      skipped += 1
      continue
    }
    nextScheduleId += 1
    MOCK_SCHEDULES.push({ ...s, schedulingId: nextScheduleId, workDate: targetDate, publishStatus: 0, usedQuota: 0, remainQuota: s.totalQuota })
    created += 1
  }
  return mockResult({ created, skipped, message: `复制上周完成：新建 ${created} 条，跳过 ${skipped} 条` })
}

export function mockApplyScheduleTemplate(data) {
  return mockCopyScheduleWeek({ ...data, sourceWeekStart: data.weekStart, targetWeekStart: data.weekStart })
}

export function mockBatchPublishSchedules(data) {
  const weekStart = alignMondayIso(data.weekStart)
  const weekEnd = addDaysIso(weekStart, 6)
  let published = 0
  for (const s of MOCK_SCHEDULES) {
    if (s.deptId === Number(data.deptId) && s.workDate >= weekStart && s.workDate <= weekEnd && (s.publishStatus ?? 1) === 0) {
      s.publishStatus = 1
      published += 1
    }
  }
  return mockResult({ published, message: `已发布 ${published} 条排班` })
}

export function mockFetchScheduleTemplate(employeeId) {
  const slots = MOCK_TEMPLATES.get(Number(employeeId)) || []
  return mockResult({ employeeId: Number(employeeId), slots })
}

export function mockReplaceScheduleTemplate(employeeId, data) {
  const slots = (data.slots || []).map((s) => ({
    ...s,
    totalQuota: s.totalQuota ?? defaultQuota(s.registLevelId),
    enabled: s.enabled !== false,
  }))
  MOCK_TEMPLATES.set(Number(employeeId), slots)
  return mockResult({ employeeId: Number(employeeId), slots, message: '固定模板已保存' })
}
