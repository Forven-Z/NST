package com.hospital.common.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckReportComposerTest {

    @Test
    void diagnosticImpressionUsesCheckWordingNotLabTemplate() {
        String text = CheckReportComposer.generateAiReportStub("胸部 CT", "无异常");
        assertFalse(text.startsWith("【诊断印象】"));
        assertFalse(text.contains("【AI 智能检验报告】"));
        assertFalse(text.contains("参考范围"));
        assertFalse(text.contains("【基于检查所见归纳】"));
        assertTrue(text.contains("双肺野清晰"));
        assertTrue(text.contains("AI 提示："));
    }

    @Test
    void diagnosticImpressionSummarizesAbnormalFindings() {
        String text = CheckReportComposer.generateAiReportStub("头部 CT", "左侧基底节区点状高密度影");
        assertFalse(text.contains("【诊断印象】"));
        assertTrue(text.contains("结合 CT 所见：左侧基底节区点状高密度影"));
        assertFalse(text.contains("【AI 智能检验报告】"));
    }

    @Test
    void normalizeStripsLegacyDiagnosticTitle() {
        assertEquals(
                "双肺野清晰。",
                CheckReportComposer.normalizeAiReportBody("【诊断印象】\n双肺野清晰。"));
    }

    @Test
    void emptyFindingsYieldsEmptyImpression() {
        assertTrue(CheckReportComposer.generateAiReportStub("头部 CT", "  ").isBlank());
    }
}
