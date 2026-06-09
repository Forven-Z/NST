package com.hospital.his.dto.patient;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AddFamilyMemberRequest {

    @NotBlank(message = "姓名不能为空")
    private String realName;

    private Integer gender;

    /** 有身份证时必填；无身份证患儿留空 */
    private String idCard;

    /** 无身份证号患儿；true 时须填写陪诊人信息，就诊人 idCard/phone 留空 */
    private Boolean noIdCard;

    private LocalDate birthDate;

    /** 选填；儿童可留空。非空时须为 11 位且全院唯一（不可与他人重复） */
    private String phone;

    /** 联系住址；挂号时会写入病历快照 */
    private String address;

    /** 0本人 1父母 2配偶 3子女 4其他 */
    private Integer relationType;

    /** 无身份证患儿必填：陪诊人/监护人 */
    private String guardianName;

    private String guardianIdCard;

    private String guardianPhone;
}
