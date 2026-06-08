package com.hospital.his.dto.patient;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class PatientProfileResponse {

    private Long id;
    private String medicalRecordNo;
    private String realName;
    private Integer gender;
    private LocalDate birthDate;
    private String phone;
    private String idCard;
    private String address;
    private Long settleCategoryId;
    private String settleCategoryName;
    /** 身份证合并后需更换 Token 时为 true */
    private Boolean identityMerged;
    private String accessToken;
    private Integer expiresIn;
}
