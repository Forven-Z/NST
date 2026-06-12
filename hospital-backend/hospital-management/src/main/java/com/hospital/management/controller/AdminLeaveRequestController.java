package com.hospital.management.controller;

import com.hospital.common.Result;
import com.hospital.management.dto.LeaveRejectRequest;
import com.hospital.management.service.LeaveRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/leave-requests")
@RequiredArgsConstructor
public class AdminLeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) Integer status) {
        return Result.success(leaveRequestService.listAdmin(status));
    }

    @PostMapping("/{id}/approve")
    public Result<Map<String, Object>> approve(@PathVariable Long id,
                                               @RequestBody(required = false) Map<String, String> body) {
        String adminName = body != null ? body.get("adminName") : null;
        return Result.success(leaveRequestService.approve(id, adminName));
    }

    @PostMapping("/{id}/reject")
    public Result<Map<String, Object>> reject(@PathVariable Long id,
                                              @RequestBody LeaveRejectRequest request) {
        return Result.success(leaveRequestService.reject(id, request));
    }
}
