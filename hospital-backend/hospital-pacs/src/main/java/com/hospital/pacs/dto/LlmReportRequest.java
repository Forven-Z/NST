package com.hospital.pacs.dto;

import lombok.Data;

@Data
public class LlmReportRequest {

    /** 医师填写的 CT 所见；生成诊断印象的唯一输入 */
    private String findingsText;
}
