package com.hospital.disposal.dto;

import lombok.Data;

@Data
public class DisposalResultRequest {

    /** 兼容旧版整段文本 */
    private String resultText;

    /** 处置过程 */
    private String processText;

    /** 观察与结果 / 签阅 */
    private String outcomeText;

    /** @deprecated 处置无 AI，忽略 */
    @Deprecated
    private String aiReportText;

    /** 双签：true 时仅更新审核人（当前登录账号） */
    private Boolean signAsReviewerOnly;

    /** 双签：true 时仅写入记录人，审核人留空待他人签阅 */
    private Boolean pendingReview;
}
