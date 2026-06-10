package com.hospital.his.dto.registrar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class WindowRegisterRequest {

    @NotBlank(message = "patientName 不能为空")
    private String patientName;

    private Integer gender;
    private LocalDate birthDate;
    private Integer age;
    private String idCard;
    private String phone;
    private String address;
    private Long settleCategoryId;
    private Boolean needRecordBook;

    @NotNull
    private Long schedulingId;
    @NotNull
    private Long deptId;
    @NotNull
    private Long employeeId;
    @NotNull
    private Long registLevelId;
}
