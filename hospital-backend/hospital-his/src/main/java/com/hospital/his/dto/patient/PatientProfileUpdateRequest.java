package com.hospital.his.dto.patient;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientProfileUpdateRequest {

    private String realName;
    private Integer gender;
    private LocalDate birthDate;
    private String phone;
    private String idCard;
    private String address;
    private Long settleCategoryId;
}
