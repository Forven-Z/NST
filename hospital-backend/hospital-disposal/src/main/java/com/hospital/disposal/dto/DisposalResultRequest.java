package com.hospital.disposal.dto;

import lombok.Data;

@Data
public class DisposalResultRequest {

    private String resultText;
    private String resultAttachment;
    private String aiReportText;
    private String doctorReportText;
}
