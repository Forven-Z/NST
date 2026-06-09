package com.hospital.his.dto.patient;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class WechatLoginRequest {

    @NotBlank(message = "code 不能为空")
    private String code;

    /** 微信昵称；缺省时用 realName */
    private String nickName;

    /** 本人真实姓名（登录建档） */
    private String realName;

    private String avatarUrl;

    /** 身份证号；若家属预建档案已存在同身份证 patient，首次登录直接合并绑定 */
    private String idCard;

    /** 1 男 2 女 */
    private Integer gender;

    private LocalDate birthDate;

    /** 本人手机号；非空时须 11 位且全院唯一 */
    private String phone;

    private String address;
}
