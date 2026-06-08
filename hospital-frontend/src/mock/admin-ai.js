import { mockResult } from '../utils/mock'
import { mockAiSchedulingSuggestions } from './ai-reports'
import { MOCK_SCHEDULES } from './dict'

export function mockAiSchedulingSuggest(params) {
  let list = [...MOCK_SCHEDULES]
  if (params?.deptId) list = list.filter((s) => s.deptId === Number(params.deptId))
  return mockResult({
    stub: true,
    generatedAt: new Date().toISOString(),
    suggestions: mockAiSchedulingSuggestions(list),
    message: '【Mock】AI 排班建议已生成，可点击「应用 AI 替换」或手工编辑',
  })
}

export function mockApplyAiSchedulingReplace(schedulingId, proposedSchedule) {
  const row = MOCK_SCHEDULES.find((s) => s.schedulingId === Number(schedulingId))
  if (!row) throw new Error('排班记录不存在')
  if (!proposedSchedule) throw new Error('无可应用的 AI 建议')
  Object.assign(row, proposedSchedule, { aiApplied: true })
  return mockResult({
    schedulingId: row.schedulingId,
    ...row,
    message: '已应用 AI 推荐排班',
  })
}

export function mockUpdateAdminSchedule(schedulingId, data) {
  const row = MOCK_SCHEDULES.find((s) => s.schedulingId === Number(schedulingId))
  if (!row) throw new Error('排班记录不存在')
  const totalQuota = Number(data.totalQuota ?? row.totalQuota)
  const remainQuota = Math.min(Number(data.remainQuota ?? row.remainQuota), totalQuota)
  Object.assign(row, {
    employeeName: data.employeeName ?? row.employeeName,
    timeRange: data.timeRange ?? row.timeRange,
    totalQuota,
    remainQuota,
    usedQuota: totalQuota - remainQuota,
    registFee: Number(data.registFee ?? row.registFee),
    manualEdited: true,
  })
  return mockResult({
    schedulingId: row.schedulingId,
    ...row,
    message: '排班已手工更新',
  })
}
