package com.hospital.his.controller.patient;

import com.hospital.common.Result;
import com.hospital.his.dto.patient.WechatLoginRequest;
import com.hospital.his.dto.patient.WechatLoginResponse;
import com.hospital.his.service.WechatAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patient/auth")
@RequiredArgsConstructor
public class PatientAuthController {

    private final WechatAuthService wechatAuthService;

    @PostMapping("/wechat")
    public Result<WechatLoginResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return Result.success(wechatAuthService.login(request));
    }
}
