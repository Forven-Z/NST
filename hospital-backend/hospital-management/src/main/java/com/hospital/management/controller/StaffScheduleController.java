package com.hospital.management.controller;

import com.hospital.common.Result;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.management.dto.LeaveRejectRequest;
import com.hospital.management.dto.LeaveRequestWriteRequest;
import com.hospital.management.security.AuthContextHolder;
import com.hospital.management.service.LeaveRequestService;
import com.hospital.management.service.SchedulingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffScheduleController {

    private final SchedulingService schedulingService;
    private final LeaveRequestService leaveRequestService;

    @GetMapping("/my-schedules")
    public Result<Map<String, Object>> mySchedules(
            @RequestParam Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDateFrom) {
        requireSelf(employeeId);
        return Result.success(schedulingService.listMySchedules(employeeId, workDateFrom));
    }

    @PostMapping("/schedules/{schedulingId}/leave-requests")
    public Result<Map<String, Object>> submitLeave(
            @PathVariable Long schedulingId,
            @Valid @RequestBody LeaveRequestWriteRequest request) {
        requireSelf(request.getEmployeeId());
        return Result.success(leaveRequestService.submitLeave(
                schedulingId, request.getEmployeeId(), request.getReason()));
    }

    @PostMapping("/leave-requests/{leaveRequestId}/cancel")
    public Result<Map<String, Object>> cancelLeave(
            @PathVariable Long leaveRequestId,
            @RequestBody Map<String, Long> body) {
        Long employeeId = body.get("employeeId");
        requireSelf(employeeId);
        return Result.success(leaveRequestService.cancelLeave(leaveRequestId, employeeId));
    }

    private void requireSelf(Long employeeId) {
        Long jwtEmployeeId = AuthContextHolder.require().getEmployeeId();
        if (jwtEmployeeId == null || !jwtEmployeeId.equals(employeeId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作本人排班");
        }
    }
}
