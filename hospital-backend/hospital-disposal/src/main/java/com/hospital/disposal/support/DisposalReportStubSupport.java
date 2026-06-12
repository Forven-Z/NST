package com.hospital.disposal.support;

import com.hospital.common.support.MedTechReportSupport;
import com.hospital.common.support.MedTechReportSupport.ParsedPublishedText;

public final class DisposalReportStubSupport {

    private static final String DEFAULT_INSTRUMENT = """
            【执行记录 · 只读】
            处置过程由执行护士/技师系统记录，本区域不可修改。""";

    private static final String DEFAULT_AI_REPORT = """
            【AI 处置摘要】
            处置过程顺利，患者生命体征平稳，请执行医师审核。""";

    private DisposalReportStubSupport() {
    }

    public static String instrumentDataFor(String itemName) {
        String data = MedTechReportSupport.instrumentDataFor(itemName);
        if (data.contains("LIS 仪器") || data.contains("检验指标")
                || data.contains("影像登记") || data.contains("CT")) {
            return DEFAULT_INSTRUMENT;
        }
        return data;
    }

    public static String aiReportFor(String itemName) {
        String report = MedTechReportSupport.aiReportFor(itemName);
        if (report.contains("检验报告") || report.contains("检查报告") || report.contains("影像")) {
            return DEFAULT_AI_REPORT;
        }
        return report;
    }

    public static String composeResultText(String ai, String doctor) {
        return MedTechReportSupport.composeResultText(ai, doctor);
    }

    public static ParsedPublishedText parsePublishedText(String resultText) {
        return MedTechReportSupport.parsePublishedText(resultText);
    }
}
