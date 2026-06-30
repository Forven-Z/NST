package com.hospital.pacs.dto;

import lombok.Data;

@Data
public class CheckResultRequest {

    /** 兼容旧版：直接提交整段 resultText */
    private String resultText;

    /** 检查所见（上方数据区） */
    private String findingsText;

    private String aiReportText;

    private String doctorReportText;

    /** 双签：true 时仅更新审核人（当前登录账号） */
    private Boolean signAsReviewerOnly;

    /** 双签：true 时仅写入报告人，审核人留空待他人签阅 */
    private Boolean pendingReview;
}
