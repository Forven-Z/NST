package com.hospital.management.client;

import com.hospital.common.Result;
import com.hospital.management.client.dto.StaffAccountCreateFeignRequest;
import com.hospital.management.client.dto.StaffAccountUpdateFeignRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = AuthStaffFeignClient.SERVICE_NAME)
public interface AuthStaffFeignClient {
    String SERVICE_NAME = "hospital-auth";

    @PostMapping("/internal/staff/accounts")
    Result<Map<String, Object>> createAccount(@RequestBody StaffAccountCreateFeignRequest request);

    @PutMapping("/internal/staff/accounts/{employeeId}")
    Result<Map<String, Object>> updateAccount(@PathVariable("employeeId") Long employeeId,
                                              @RequestBody StaffAccountUpdateFeignRequest request);

    @DeleteMapping("/internal/staff/accounts/{employeeId}")
    Result<Map<String, Object>> disableAccount(@PathVariable("employeeId") Long employeeId);
}
