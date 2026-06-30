package com.hospital.common.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabReportComposerTest {

    @Test
    void parsePublishedText_structuredFormat_splitsAiAndDoctor() {
        String stored = LabReportComposer.composeResultText(
                List.of(Map.of(
                        "name", "白细胞",
                        "result", "12.8",
                        "unit", "×10⁹/L",
                        "refRange", "3.5-9.5",
                        "flag", "H"
                )),
                "【AI 智能检验报告】白细胞偏高。",
                "复核无误，建议结合临床。"
        );

        var parsed = LabReportComposer.parsePublishedText(stored);

        assertEquals("【AI 智能检验报告】白细胞偏高。", parsed.aiReportText());
        assertEquals("复核无误，建议结合临床。", parsed.doctorReportText());
        assertTrue(stored.contains("【检验结果】"));
        assertTrue(parsed.aiReportText().isBlank() || !parsed.aiReportText().contains("白细胞  12.8"));
    }

    @Test
    void parsePublishedText_legacyAiDoctorFormat() {
        var parsed = LabReportComposer.parsePublishedText("AI：分析内容\n医师：医师意见");
        assertEquals("分析内容", parsed.aiReportText());
        assertEquals("医师意见", parsed.doctorReportText());
    }

    @Test
    void parsePublishedText_aiOnlyWithoutDoctorSection() {
        var parsed = LabReportComposer.parsePublishedText("""
                【检验结果】
                白细胞  12.8 ×10⁹/L  参考 3.5-9.5 ↑

                【诊断分析】
                仅 AI 分析
                """);
        assertEquals("仅 AI 分析", parsed.aiReportText());
        assertEquals("", parsed.doctorReportText());
    }
}
