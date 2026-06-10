import { mockResult } from '../utils/mock'
import { mockAiSchedulingSuggestions } from './ai-reports'
import { MOCK_REGIST_LEVELS, MOCK_SCHEDULES } from './dict'
import { getDepartmentById, getEmployeeById } from './staff-registry'
import {
  getApprovedLeaveSchedulingIds,
  getPendingLeaveCount,
  markLeaveSubstituted,
  mockSubstituteProposal,
} from './scheduling-leave'

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
  let list = [...MOCK_SCHEDULES].filter((s) => (s.publishStatus ?? 1) !== 2)
  if (params?.deptId) list = list.filter((s) => s.deptId === Number(params.deptId))
  const pendingLeave = getPendingLeaveCount()
  return mockResult({
    stub: true,
    generatedAt: new Date().toISOString(),
    suggestions: buildLeaveAwareSuggestions(list),
    pendingLeaveCount: pendingLeave,
    message: pendingLeave
      ? `【Mock】检测到 ${pendingLeave} 条待审批请假；已优先生成替班建议`
      : '【Mock】AI 排班建议已生成，可点击「应用 AI 替换」或手工编辑',
  })
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
    ? `已应用替班：${row.employeeName} 顶替 ${data.substituteFor || '原职员'}`
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
