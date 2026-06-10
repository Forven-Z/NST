import request from './request'
import { useMock } from '../utils/mock'
import {
  mockAdminLeaveRequests,
  mockApproveLeaveRequest,
  mockCancelLeaveRequest,
  mockMySchedules,
  mockRejectLeaveRequest,
  mockSubmitLeaveRequest,
} from '../mock/scheduling-leave'

export function fetchMySchedules(params) {
  if (useMock()) return mockMySchedules(params.employeeId, params)
  return request.get('/staff/my-schedules', { params })
}

export function submitScheduleLeaveRequest(schedulingId, data) {
  if (useMock()) {
    return mockSubmitLeaveRequest(data.employeeId, schedulingId, data.reason)
  }
  return request.post(`/staff/schedules/${schedulingId}/leave-requests`, data)
}

export function cancelLeaveRequest(leaveRequestId, data) {
  if (useMock()) return mockCancelLeaveRequest(data.employeeId, leaveRequestId)
  return request.post(`/staff/leave-requests/${leaveRequestId}/cancel`, data)
}

export function fetchAdminLeaveRequests(params) {
  if (useMock()) return mockAdminLeaveRequests(params)
  return request.get('/admin/leave-requests', { params })
}

export function approveLeaveRequest(leaveRequestId, data) {
  if (useMock()) return mockApproveLeaveRequest(leaveRequestId, data?.adminName)
  return request.post(`/admin/leave-requests/${leaveRequestId}/approve`, data)
}

export function rejectLeaveRequest(leaveRequestId, data) {
  if (useMock()) return mockRejectLeaveRequest(leaveRequestId, data?.remark, data?.adminName)
  return request.post(`/admin/leave-requests/${leaveRequestId}/reject`, data)
}
