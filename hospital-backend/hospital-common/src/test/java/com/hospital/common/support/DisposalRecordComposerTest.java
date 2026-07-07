package com.hospital.common.support;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DisposalRecordComposerTest {

    @Test
    void composeResultText_buildsStructuredSections() {
        String text = DisposalRecordComposer.composeResultText("留置针穿刺", "患者无不适");

        assertThat(text).contains("【处置过程】");
        assertThat(text).contains("留置针穿刺");
        assertThat(text).contains("【观察与结果】");
        assertThat(text).contains("患者无不适");
    }

    @Test
    void composeResultText_blankInputs() {
        assertThat(DisposalRecordComposer.composeResultText("", "")).isEmpty();
        assertThat(DisposalRecordComposer.composeResultText(null, null)).isEmpty();
    }

    @Test
    void parsePublishedText_structuredFormat() {
        String stored = """
                【处置过程】
                温盐水洗胃
                【观察与结果】
                生命体征平稳
                """;

        DisposalRecordComposer.ParsedRecord parsed = DisposalRecordComposer.parsePublishedText(stored);

        assertThat(parsed.processText()).contains("温盐水洗胃");
        assertThat(parsed.outcomeText()).contains("生命体征平稳");
    }

    @Test
    void summarize_shortTextUnchanged() {
        assertThat(DisposalRecordComposer.summarize("步骤A", "结果B"))
                .contains("步骤A")
                .contains("结果B");
    }

    @Test
    void summarize_longTextTruncated() {
        String longProcess = "步".repeat(100);
        String summary = DisposalRecordComposer.summarize(longProcess, "");

        assertThat(summary).hasSize(81);
        assertThat(summary).endsWith("…");
    }

    @Test
    void summarize_emptyShowsDefaultHint() {
        assertThat(DisposalRecordComposer.summarize("", ""))
                .isEqualTo("处置记录已出，点击查看详情");
    }

    @Test
    void processPlaceholderFor_knownItems() {
        assertThat(DisposalRecordComposer.processPlaceholderFor("洗胃")).contains("胃管");
        assertThat(DisposalRecordComposer.processPlaceholderFor("静脉输液")).contains("静滴");
        assertThat(DisposalRecordComposer.processPlaceholderFor("未知项目")).contains("描述处置步骤");
    }

    @Test
    void composeView_buildsHeaderAndFooter() {
        OffsetDateTime executeTime = OffsetDateTime.parse("2026-06-04T10:30:00+08:00");
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("disposalRequestId", 62001L);
        context.put("itemName", "静脉输液");
        context.put("status", 40);
        context.put("patientName", "张三");
        context.put("gender", 1);
        context.put("age", 35);
        context.put("medicalRecordNo", "MR202606040100");
        context.put("departmentName", "急诊科");
        context.put("executeTime", executeTime);
        context.put("executorName", "李护士");
        context.put("reviewerName", "");

        Map<String, Object> view = DisposalRecordComposer.composeView(context, "穿刺成功", "无渗漏");

        assertThat(view.get("reportType")).isEqualTo("disposal");
        assertThat(view.get("recordNo")).isEqualTo("DIS-62001");
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) view.get("header");
        assertThat(header.get("patientName")).isEqualTo("张三");
        assertThat(header.get("genderLabel")).isEqualTo("男");
        assertThat(header.get("ageLabel")).isEqualTo("35岁");
        @SuppressWarnings("unchecked")
        Map<String, Object> footer = (Map<String, Object>) view.get("footer");
        assertThat(footer.get("reviewerName")).isEqualTo("待审核");
        assertThat(String.valueOf(view.get("resultText"))).contains("穿刺成功");
    }
}
