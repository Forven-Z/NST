package com.hospital.his.dto.patient;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateRegisterRequest {

    @NotNull(message = "schedulingId 不能为空")
    private Long schedulingId;

    @NotNull(message = "deptId 不能为空")
    private Long deptId;

    @NotNull(message = "employeeId 不能为空")
    private Long employeeId;

    @NotNull(message = "registLevelId 不能为空")
    private Long registLevelId;

    private Long settleCategoryId;
}
