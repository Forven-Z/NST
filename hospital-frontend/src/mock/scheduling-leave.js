/**
 * Mock：排班请假（待后端落库 scheduling_leave_request 表）
 *
 * 预定 API（仅前端 Mock，未写入 docs/API.md）：
 * - GET  /staff/my-schedules?employeeId=
 * - POST /staff/schedules/{schedulingId}/leave-requests  { reason }
 * - GET  /admin/leave-requests?status=0
 * - POST /admin/leave-requests/{id}/approve
 * - POST /admin/leave-requests/{id}/reject  { remark }
 *
 * 预定库表字段：scheduling_id, employee_id, reason, status(0待审/1批准/2驳回/3撤销),
 * approve_admin_id, approve_time, create_time
 */
import { mockResult } from '../utils/mock'
import {
  getSubstitutePoolByDept,
  MOCK_ALL_DEPARTMENTS,
  MOCK_SCHEDULES,
} from './dict'

/** @type {Array<Record<string, unknown>>} */
const leaveRequests = []
let leaveSeq = 9001

const STATUS_LABEL = {
  0: '待审批',
  1: '已批准',
  2: '已驳回',
  3: '已撤销',
  4: '已替班',
}

function todayStr() {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  return d.toISOString().slice(0, 10)
}

function findSchedule(schedulingId) {
  const row = MOCK_SCHEDULES.find((s) => s.schedulingId === Number(schedulingId))
  if (!row) throw new Error('排班记录不存在')
  return row
}

function activeLeaveForSchedule(schedulingId) {
  return leaveRequests.find(
    (l) => l.schedulingId === Number(schedulingId) && (l.status === 0 || l.status === 1),
  )
}

function approvedLeaveForSchedule(schedulingId) {
  return leaveRequests.find(
    (l) => l.schedulingId === Number(schedulingId) && l.status === 1,
  )
}

/** 手工/AI 换人完成后，将已批准请假标记为已替班 */
export function markLeaveSubstituted(schedulingId, substituteEmployeeId, substituteEmployeeName) {
  const approved = approvedLeaveForSchedule(schedulingId)
  if (!approved) return false
  if (Number(substituteEmployeeId) === approved.employeeId) return false
  approved.status = 4
  approved.statusLabel = STATUS_LABEL[4]
  approved.substituteEmployeeId = Number(substituteEmployeeId)
  approved.substituteEmployeeName = substituteEmployeeName || ''
  approved.substituteTime = new Date().toISOString()
  const sched = findSchedule(schedulingId)
  sched.substituteRequired = false
  sched.leaveApproved = false
  return true
}

function enrichSchedule(row) {
  const leave = activeLeaveForSchedule(row.schedulingId)
  const dept = MOCK_ALL_DEPARTMENTS.find((d) => d.id === row.deptId)
  return {
    ...row,
    publishStatus: row.publishStatus ?? 1,
    deptName: dept?.deptName || '',
    leaveRequestId: leave?.leaveRequestId ?? null,
    leaveStatus: leave?.status ?? null,
    leaveStatusLabel: leave ? STATUS_LABEL[leave.status] : null,
    leaveReason: leave?.reason ?? null,
    canRequestLeave: !leave && row.workDate >= todayStr() && (row.publishStatus ?? 1) === 1,
  }
}

export function mockMySchedules(employeeId, params = {}) {
  let list = MOCK_SCHEDULES.filter(
    (s) => s.employeeId === Number(employeeId) && (s.publishStatus ?? 1) !== 2,
  )
  if (params.workDateFrom) {
    list = list.filter((s) => s.workDate >= params.workDateFrom)
  }
  list.sort((a, b) => {
    if (a.workDate !== b.workDate) return a.workDate.localeCompare(b.workDate)
    return a.noonType - b.noonType
  })
  return mockResult({ list: list.map(enrichSchedule), page: 1, pageSize: 100 })
}

export function mockSubmitLeaveRequest(employeeId, schedulingId, reason) {
  const sched = findSchedule(schedulingId)
  if (sched.employeeId !== Number(employeeId)) throw new Error('只能对自己的排班申请请假')
  if ((sched.publishStatus ?? 1) !== 1) throw new Error('仅已发布排班可申请请假')
  if (sched.workDate < todayStr()) throw new Error('不能对已过期的排班请假')
  if (!reason?.trim()) throw new Error('请填写请假原因')
  if (activeLeaveForSchedule(schedulingId)) throw new Error('该班次已有待处理或已批准的请假')

  leaveSeq += 1
  const row = {
    leaveRequestId: leaveSeq,
    schedulingId: Number(schedulingId),
    employeeId: Number(employeeId),
    employeeName: sched.employeeName,
    workDate: sched.workDate,
    noonLabel: sched.noonLabel,
    timeRange: sched.timeRange,
    deptId: sched.deptId,
    registLevelName: sched.registLevelName,
    reason: reason.trim(),
    status: 0,
    statusLabel: STATUS_LABEL[0],
    createTime: new Date().toISOString(),
    approveTime: null,
    approveAdminName: null,
    usedQuota: sched.usedQuota,
    remainQuota: sched.remainQuota,
  }
  leaveRequests.push(row)
  sched.substituteRequired = true
  return mockResult({ ...row, message: '请假申请已提交，等待管理员审批' })
}

export function mockAdminLeaveRequests(params = {}) {
  let list = [...leaveRequests]
  if (params.status !== undefined && params.status !== null && params.status !== '') {
    list = list.filter((l) => l.status === Number(params.status))
  }
  list.sort((a, b) => b.createTime.localeCompare(a.createTime))
  return mockResult({ list, page: 1, pageSize: 50 })
}

export function mockApproveLeaveRequest(leaveRequestId, adminName = '管理员') {
  const row = leaveRequests.find((l) => l.leaveRequestId === Number(leaveRequestId))
  if (!row) throw new Error('请假申请不存在')
  if (row.status !== 0) throw new Error('仅待审批申请可批准')
  row.status = 1
  row.statusLabel = STATUS_LABEL[1]
  row.approveTime = new Date().toISOString()
  row.approveAdminName = adminName
  const sched = findSchedule(row.schedulingId)
  sched.substituteRequired = true
  sched.leaveApproved = true
  return mockResult({ ...row, message: '已批准请假，请安排替班医生（AI 建议或手工编辑）' })
}

export function mockRejectLeaveRequest(leaveRequestId, remark, adminName = '管理员') {
  const row = leaveRequests.find((l) => l.leaveRequestId === Number(leaveRequestId))
  if (!row) throw new Error('请假申请不存在')
  if (row.status !== 0) throw new Error('仅待审批申请可驳回')
  row.status = 2
  row.statusLabel = STATUS_LABEL[2]
  row.approveTime = new Date().toISOString()
  row.approveAdminName = adminName
  row.rejectRemark = remark || ''
  const sched = findSchedule(row.schedulingId)
  sched.substituteRequired = false
  return mockResult({ ...row, message: '已驳回请假申请' })
}

export function mockCancelLeaveRequest(employeeId, leaveRequestId) {
  const row = leaveRequests.find((l) => l.leaveRequestId === Number(leaveRequestId))
  if (!row) throw new Error('请假申请不存在')
  if (row.employeeId !== Number(employeeId)) throw new Error('无权撤销')
  if (row.status !== 0) throw new Error('仅待审批申请可撤销')
  row.status = 3
  row.statusLabel = STATUS_LABEL[3]
  const sched = findSchedule(row.schedulingId)
  sched.substituteRequired = false
  return mockResult({ ...row, message: '已撤销请假申请' })
}

/** 供 AI 排班建议：已批准且尚未完成替班的 schedulingId 列表 */
export function getApprovedLeaveSchedulingIds() {
  return leaveRequests
    .filter((l) => l.status === 1)
    .filter((l) => {
      const sched = MOCK_SCHEDULES.find((s) => s.schedulingId === l.schedulingId)
      return sched && sched.employeeId === l.employeeId
    })
    .map((l) => l.schedulingId)
}

export function getPendingLeaveCount() {
  return leaveRequests.filter((l) => l.status === 0).length
}

/** 管理端排班列表附加请假标记 */
export function enrichScheduleAdminRow(row) {
  const pending = leaveRequests.find(
    (l) => l.schedulingId === row.schedulingId && l.status === 0,
  )
  const approved = approvedLeaveForSchedule(row.schedulingId)
  const substituted = leaveRequests.find(
    (l) => l.schedulingId === row.schedulingId && l.status === 4,
  )
  const dept = MOCK_ALL_DEPARTMENTS.find((d) => d.id === row.deptId)
  const stillNeedsSub = approved && row.employeeId === approved.employeeId
  return {
    ...row,
    deptName: dept?.deptName || '',
    pendingLeave: !!pending,
    approvedLeave: !!approved,
    leaveSubstituted: !!substituted,
    leaveRequestId: pending?.leaveRequestId || approved?.leaveRequestId || substituted?.leaveRequestId || null,
    leaveReason: pending?.reason || approved?.reason || substituted?.reason || null,
    needsSubstitute: stillNeedsSub || (!!row.substituteRequired && !approved && !substituted),
  }
}

/** 为已批准请假的班次生成替班医生建议 */
export function mockSubstituteProposal(scheduleRow) {
  const pool = getSubstitutePoolByDept(scheduleRow.deptId).filter(
    (d) => d.employeeId !== scheduleRow.employeeId,
  )
  const substitute = pool[scheduleRow.schedulingId % pool.length] || pool[0]
  if (!substitute) return null
  return {
    employeeId: substitute.employeeId,
    employeeName: substitute.realName,
    employeeTitle: substitute.title,
    totalQuota: scheduleRow.totalQuota,
    remainQuota: scheduleRow.remainQuota,
    substituteFor: scheduleRow.employeeName,
    aiOptimized: true,
    leaveSubstitute: true,
  }
}
