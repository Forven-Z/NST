package com.hospital.patient.dto.patient;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientLoginRequest {

    @NotBlank(message = "姓名不能为空")
    private String realName;

    @NotBlank(message = "身份证号不能为空")
    private String idCard;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    private Integer gender;

    private LocalDate birthDate;

    private String address;
}
