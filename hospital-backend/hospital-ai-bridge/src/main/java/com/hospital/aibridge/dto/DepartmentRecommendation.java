package com.hospital.aibridge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentRecommendation {

    private Long deptId;
    private String deptCode;
    private String deptName;
    private String matchedDeptName;
    private Double confidence;
    private String reason;
    private String nextAction;
}
