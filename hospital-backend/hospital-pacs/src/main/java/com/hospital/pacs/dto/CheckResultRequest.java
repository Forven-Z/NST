package com.hospital.pacs.dto;

import lombok.Data;

@Data
public class CheckResultRequest {

    private String resultText;

    private String resultAttachment;

    private String aiReportText;

    private String doctorReportText;
}
