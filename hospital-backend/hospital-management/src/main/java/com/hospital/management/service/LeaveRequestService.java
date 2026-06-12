package com.hospital.management.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.management.dto.LeaveRejectRequest;
import com.hospital.management.repository.LeaveRequestRepository;
import com.hospital.management.repository.SchedulingRepository;
import com.hospital.management.support.NoonTypeSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private static final Map<Integer, String> STATUS_LABEL = Map.of(
            0, "待审批", 1, "已批准", 2, "已驳回", 3, "已撤销", 4, "已替班");

    private final LeaveRequestRepository leaveRequestRepository;
    private final SchedulingRepository schedulingRepository;

    public Map<String, Object> listAdmin(Integer status) {
        List<Map<String, Object>> list = leaveRequestRepository.listAdmin(status).stream()
                .map(this::enrichAdminRow)
                .toList();
        return Map.of("list", list, "page", 1, "pageSize", 100);
    }

    public Map<String, Object> submitLeave(Long schedulingId, Long employeeId, String reason) {
        Map<String, Object> sched = schedulingRepository.findByIdForUpdate(schedulingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "排班记录不存在"));
        if (!employeeId.equals(sched.get("employeeId"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能对自己的排班申请请假");
        }
        if ((Integer) sched.get("publishStatus") != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已发布排班可申请请假");
        }
        LocalDate workDate = (LocalDate) sched.get("workDate");
        if (workDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能对已过期的排班请假");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写请假原因");
        }
        if (leaveRequestRepository.hasActiveLeave(schedulingId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该班次已有待处理或已批准的请假");
        }
        try {
            long id = leaveRequestRepository.insert(schedulingId, employeeId, reason.trim());
            Map<String, Object> row = leaveRequestRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "请假申请不存在"));
            Map<String, Object> result = enrichAdminRow(row);
            result.put("message", "请假申请已提交，等待管理员审批");
            return result;
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该班次已有待处理或已批准的请假");
        }
    }

    public Map<String, Object> cancelLeave(Long leaveRequestId, Long employeeId) {
        if (leaveRequestRepository.cancel(leaveRequestId, employeeId) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅待审批申请可撤销");
        }
        return Map.of("leaveRequestId", leaveRequestId, "message", "已撤销请假申请");
    }

    public Map<String, Object> approve(Long leaveRequestId, String adminName) {
        Map<String, Object> row = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "请假申请不存在"));
        if ((Integer) row.get("status") != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅待审批申请可批准");
        }
        if (leaveRequestRepository.updateStatus(leaveRequestId, 1, null) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅待审批申请可批准");
        }
        Map<String, Object> updated = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "请假申请不存在"));
        Map<String, Object> result = enrichAdminRow(updated);
        result.put("approveAdminName", adminName);
        result.put("message", "已批准请假，请安排替班医生（AI 建议或手工编辑）");
        return result;
    }

    public Map<String, Object> reject(Long leaveRequestId, LeaveRejectRequest request) {
        Map<String, Object> row = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "请假申请不存在"));
        if ((Integer) row.get("status") != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅待审批申请可驳回");
        }
        if (leaveRequestRepository.updateStatus(leaveRequestId, 2, request.getRemark()) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅待审批申请可驳回");
        }
        Map<String, Object> result = enrichAdminRow(leaveRequestRepository.findById(leaveRequestId).orElseThrow());
        result.put("approveAdminName", request.getAdminName());
        result.put("message", "已驳回请假申请");
        return result;
    }

    public void markSubstitutedIfNeeded(Long schedulingId, Long newEmployeeId, Long previousEmployeeId) {
        if (newEmployeeId == null || newEmployeeId.equals(previousEmployeeId)) {
            return;
        }
        leaveRequestRepository.markSubstituted(schedulingId, newEmployeeId);
    }

    public Map<String, Object> enrichScheduleRow(Map<String, Object> scheduleRow) {
        Map<String, Object> enriched = new LinkedHashMap<>(scheduleRow);
        Long schedulingId = (Long) scheduleRow.get("schedulingId");
        Long scheduleEmployeeId = (Long) scheduleRow.get("employeeId");
        leaveRequestRepository.findLatestBySchedulingId(schedulingId).ifPresentOrElse(leave -> {
            int status = (Integer) leave.get("status");
            enriched.put("leaveRequestId", leave.get("leaveRequestId"));
            enriched.put("leaveReason", leave.get("reason"));
            enriched.put("pendingLeave", status == 0);
            enriched.put("leaveSubstituted", status == 4);
            boolean approved = status == 1;
            enriched.put("needsSubstitute", approved && scheduleEmployeeId.equals(leave.get("employeeId")));
        }, () -> {
            enriched.put("pendingLeave", false);
            enriched.put("needsSubstitute", false);
            enriched.put("leaveSubstituted", false);
        });
        return enriched;
    }

    public Map<String, Object> enrichStaffScheduleRow(Map<String, Object> scheduleRow) {
        Map<String, Object> enriched = new LinkedHashMap<>(scheduleRow);
        Long schedulingId = (Long) scheduleRow.get("schedulingId");
        LocalDate workDate = (LocalDate) scheduleRow.get("workDate");
        leaveRequestRepository.findActiveBySchedulingId(schedulingId).ifPresentOrElse(leave -> {
            int status = (Integer) leave.get("status");
            enriched.put("leaveRequestId", leave.get("leaveRequestId"));
            enriched.put("leaveStatus", status);
            enriched.put("leaveStatusLabel", STATUS_LABEL.get(status));
            enriched.put("leaveReason", leave.get("reason"));
            enriched.put("canRequestLeave", false);
        }, () -> {
            enriched.put("canRequestLeave",
                    workDate != null && !workDate.isBefore(LocalDate.now())
                            && Integer.valueOf(1).equals(scheduleRow.get("publishStatus")));
        });
        return enriched;
    }

    private Map<String, Object> enrichAdminRow(Map<String, Object> row) {
        Map<String, Object> enriched = new LinkedHashMap<>(row);
        int status = (Integer) row.get("status");
        enriched.put("statusLabel", STATUS_LABEL.get(status));
        int noonType = (Integer) row.get("noonType");
        enriched.put("noonLabel", NoonTypeSupport.label(noonType));
        enriched.put("timeRange", NoonTypeSupport.timeRange(noonType));
        return enriched;
    }
}
