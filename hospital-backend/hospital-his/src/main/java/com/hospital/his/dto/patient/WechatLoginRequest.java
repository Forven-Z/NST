package com.hospital.his.dto.patient;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WechatLoginRequest {

    @NotBlank(message = "code 不能为空")
    private String code;

    private String nickName;
    private String avatarUrl;
    /** 可选；若家属预建档案已存在同身份证 patient，首次登录直接合并绑定 */
    private String idCard;
}
