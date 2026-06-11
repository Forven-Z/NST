package com.hospital.pacs.support;

import com.hospital.common.support.MedTechReportSupport;
import com.hospital.common.support.MedTechReportSupport.ParsedPublishedText;

public final class PacsReportStubSupport {

    private static final String DEFAULT_INSTRUMENT = """
            【影像登记数据 · 只读】
            检查参数由设备/登记系统上传，本区域不可修改。""";

    private static final String DEFAULT_AI_REPORT = """
            【AI 智能检查报告】
            影像所见与参考比对后未见明显异常模式，请放射科医师审核。""";

    private PacsReportStubSupport() {}

    public static String instrumentDataFor(String itemName) {
        String data = MedTechReportSupport.instrumentDataFor(itemName);
        if (data.contains("LIS 仪器") || data.contains("检验指标")) {
            return DEFAULT_INSTRUMENT;
        }
        return data;
    }

    public static String aiReportFor(String itemName) {
        String report = MedTechReportSupport.aiReportFor(itemName);
        if (report.contains("检验报告") || report.contains("血常规")) {
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
