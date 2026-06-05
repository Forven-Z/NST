package com.hospital.his.dto.patient;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddFamilyMemberRequest {

    @NotBlank(message = "姓名不能为空")
    private String realName;

    private Integer gender;

    @NotBlank(message = "身份证号不能为空")
    private String idCard;

    private String phone;

    /** 0本人 1父母 2配偶 3子女 4其他 */
    private Integer relationType;
}
