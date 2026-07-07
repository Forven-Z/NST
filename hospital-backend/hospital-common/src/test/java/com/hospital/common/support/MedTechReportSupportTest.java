package com.hospital.common.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MedTechReportSupportTest {

    @Test
    void instrumentDataFor_knownItem() {
        assertThat(MedTechReportSupport.instrumentDataFor("血常规")).contains("白细胞 WBC");
    }

    @Test
    void instrumentDataFor_unknownItemUsesDefault() {
        assertThat(MedTechReportSupport.instrumentDataFor("未知项目")).contains("仪器原始数据");
    }

    @Test
    void instrumentDataFor_blankUsesDefault() {
        assertThat(MedTechReportSupport.instrumentDataFor("  ")).contains("LIS 仪器自动上传");
    }

    @Test
    void aiReportFor_knownItem() {
        assertThat(MedTechReportSupport.aiReportFor("洗胃")).contains("AI 处置摘要");
    }

    @Test
    void composeResultText_aiOnly() {
        assertThat(MedTechReportSupport.composeResultText("AI 报告", ""))
                .isEqualTo("AI：AI 报告");
    }

    @Test
    void composeResultText_doctorOnly() {
        assertThat(MedTechReportSupport.composeResultText("", "医师结论"))
                .isEqualTo("医师：医师结论");
    }

    @Test
    void composeResultText_bothSections() {
        String text = MedTechReportSupport.composeResultText("AI 报告", "医师结论");

        assertThat(text).isEqualTo("AI：AI 报告\n医师：医师结论");
    }

    @Test
    void parsePublishedText_structuredFormat() {
        MedTechReportSupport.ParsedPublishedText parsed = MedTechReportSupport.parsePublishedText("""
                AI：白细胞偏高
                医师：建议复查
                """);

        assertThat(parsed.aiReportText()).isEqualTo("白细胞偏高");
        assertThat(parsed.doctorReportText()).isEqualTo("建议复查");
    }

    @Test
    void parsePublishedText_legacyPlainText() {
        MedTechReportSupport.ParsedPublishedText parsed =
                MedTechReportSupport.parsePublishedText("  原始报告  ");

        assertThat(parsed.aiReportText()).isEqualTo("原始报告");
        assertThat(parsed.doctorReportText()).isEmpty();
    }

    @Test
    void parsePublishedText_blank() {
        MedTechReportSupport.ParsedPublishedText parsed = MedTechReportSupport.parsePublishedText("  ");

        assertThat(parsed.aiReportText()).isEmpty();
        assertThat(parsed.doctorReportText()).isEmpty();
    }
}
