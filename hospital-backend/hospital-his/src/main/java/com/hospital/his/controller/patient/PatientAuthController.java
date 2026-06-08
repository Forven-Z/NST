package com.hospital.his.controller.patient;

import com.hospital.common.Result;
import com.hospital.his.dto.patient.PatientLoginRequest;
import com.hospital.his.dto.patient.SwitchAccountRequest;
import com.hospital.his.dto.patient.WechatBindRequest;
import com.hospital.his.dto.patient.WechatLoginRequest;
import com.hospital.his.dto.patient.WechatLoginResponse;
import com.hospital.his.service.PatientAuthService;
import com.hospital.his.service.PatientWechatBindService;
import com.hospital.his.service.WechatAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/patient/auth")
@RequiredArgsConstructor
public class PatientAuthController {

    private final PatientAuthService patientAuthService;
    private final WechatAuthService wechatAuthService;
    private final PatientWechatBindService wechatBindService;

    /** 病人账户登录（手机号 + 身份证 + 验证码） */
    @PostMapping("/login")
    public Result<WechatLoginResponse> login(@Valid @RequestBody PatientLoginRequest request) {
        return Result.success(patientAuthService.login(request));
    }

    /** QQ 式切换当前登录的病人账户（换 JWT） */
    @PostMapping("/switch-account")
    public Result<WechatLoginResponse> switchAccount(@Valid @RequestBody SwitchAccountRequest request) {
        return Result.success(patientAuthService.switchAccount(request.getTargetPatientId()));
    }

    /** 微信支付前：将 openid 绑定到当前病人账户 */
    @PostMapping("/wechat/bind")
    public Result<Map<String, Object>> bindWechat(@Valid @RequestBody WechatBindRequest request) {
        return Result.success(wechatBindService.bindWechat(request));
    }

    /**
     * @deprecated 请使用 {@link #login}；保留兼容 Mock/旧客户端
     */
    @PostMapping("/wechat")
    public Result<WechatLoginResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return Result.success(wechatAuthService.login(request));
    }
}
