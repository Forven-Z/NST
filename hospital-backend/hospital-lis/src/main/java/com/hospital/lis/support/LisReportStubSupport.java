package com.hospital.lis.support;

import com.hospital.common.support.MedTechReportSupport;

/**
 * LIS 仪器数据 / AI 报告 STUB（规则模板，不对接真实仪器或大模型）。
 */
public final class LisReportStubSupport {

    private LisReportStubSupport() {
    }

    public static String instrumentDataFor(String itemName) {
        return MedTechReportSupport.instrumentDataFor(itemName);
    }

    public static String aiReportFor(String itemName) {
        return MedTechReportSupport.aiReportFor(itemName);
    }

    public static String composeResultText(String aiReportText, String doctorReportText) {
        return MedTechReportSupport.composeResultText(aiReportText, doctorReportText);
    }

    public static MedTechReportSupport.ParsedPublishedText parsePublishedText(String resultText) {
        return MedTechReportSupport.parsePublishedText(resultText);
    }

    public static String triageLevelForRegistLevelCode(String levelCode) {
        if ("EXPERT".equalsIgnoreCase(levelCode)) {
            return "URGENT";
        }
        return "NORMAL";
    }

    public static String triageNoteForRegistLevel(String levelName) {
        return "基于挂号级别 STUB 分诊（" + (levelName != null ? levelName : "普通号") + "）";
    }
}
