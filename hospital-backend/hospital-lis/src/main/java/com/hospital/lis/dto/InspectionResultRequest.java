package com.hospital.lis.dto;

import lombok.Data;

@Data
public class InspectionResultRequest {

    /** 兼容旧版：直接传合并后的 resultText */
    private String resultText;

    private String resultAttachment;

    /** v2.1 三段式：与 doctorReportText 合成 resultText 写入库 */
    private String aiReportText;

    private String doctorReportText;
}
