package com.hospital.his.dto.patient;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WechatLoginResponse {

    private String accessToken;
    private Long expiresIn;
    private Long patientId;
    private String medicalRecordNo;
    private String realName;
    private Boolean isNewPatient;
}
