package com.hospital.common.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MedTechReportSupport {

    private static final Pattern PUBLISHED_TEXT = Pattern.compile(
            "^AI：(?<ai>[\\s\\S]*?)(?:\\n医师：(?<doctor>[\\s\\S]*))?$");

    private static final Map<String, String> INSTRUMENT_BY_ITEM = new LinkedHashMap<>();
    private static final Map<String, String> AI_REPORT_BY_ITEM = new LinkedHashMap<>();

    static {
        INSTRUMENT_BY_ITEM.put("血常规", """
                【仪器原始数据 · 只读】
                白细胞 WBC        12.8 ×10⁹/L    参考 3.5-9.5  ↑
                红细胞 RBC        4.65×10¹²/L   参考 4.3-5.8
                血红蛋白 Hb       138 g/L       参考 130-175
                血小板 PLT        210 ×10⁹/L    参考 125-350
                中性粒细胞%       62.0 %        参考 40-75
                采样时间：仪器自动记录""");

        AI_REPORT_BY_ITEM.put("血常规", """
                【AI 智能检验报告】
                综合血常规指标：白细胞偏高，其余红细胞、血红蛋白、血小板均在参考范围内。
                AI 提示：建议结合临床症状排查感染可能，请医师审核确认。""");

        INSTRUMENT_BY_ITEM.put("头部 CT", """
                【影像登记数据 · 只读】
                模态：CT · 部位：头部
                层厚：1.0 mm · 管电压：120 kV
                扫描范围：颅顶至下颌骨
                登记时间：设备自动记录""");

        AI_REPORT_BY_ITEM.put("头部 CT", """
                【AI 智能检查报告】
                颅脑 CT 平扫：脑实质密度未见明显异常；脑室系统大小形态正常；中线结构居中。
                AI 提示：未见明显急性出血或占位征象，请放射科医师结合临床审核。""");

        INSTRUMENT_BY_ITEM.put("洗胃", """
                【执行记录 · 只读】
                体位：左侧卧位 · 胃管型号：16Fr
                入量：温盐水 500 ml · 出量：450 ml（澄清）
                生命体征：BP 118/76 mmHg · HR 78 次/分 · SpO₂ 98%
                执行时间：系统自动记录""");

        AI_REPORT_BY_ITEM.put("洗胃", """
                【AI 处置摘要】
                洗胃过程顺利，患者生命体征平稳，未见明显误吸或出血征象。
                AI 提示：请执行医师结合临床观察确认，必要时复查。""");
    }

    private static final String DEFAULT_INSTRUMENT = """
            【仪器原始数据 · 只读】
            检验指标由 LIS 仪器自动上传，本区域不可修改。""";

    private static final String DEFAULT_AI_REPORT = """
            【AI 智能检验报告】
            各项指标与参考范围比对后未见明显异常模式。""";

    private MedTechReportSupport() {}

    public static String instrumentDataFor(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return DEFAULT_INSTRUMENT;
        }
        return INSTRUMENT_BY_ITEM.getOrDefault(itemName.trim(), DEFAULT_INSTRUMENT);
    }

    public static String aiReportFor(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return DEFAULT_AI_REPORT;
        }
        return AI_REPORT_BY_ITEM.getOrDefault(itemName.trim(), DEFAULT_AI_REPORT);
    }

    public static String composeResultText(String aiReportText, String doctorReportText) {
        String ai = aiReportText != null ? aiReportText.trim() : "";
        String doctor = doctorReportText != null ? doctorReportText.trim() : "";
        if (ai.isEmpty() && doctor.isEmpty()) return "";
        if (doctor.isEmpty()) return "AI：" + ai;
        if (ai.isEmpty()) return "医师：" + doctor;
        return "AI：" + ai + "\n医师：" + doctor;
    }

    public static ParsedPublishedText parsePublishedText(String resultText) {
        if (resultText == null || resultText.isBlank()) {
            return new ParsedPublishedText("", "");
        }
        Matcher matcher = PUBLISHED_TEXT.matcher(resultText.trim());
        if (matcher.matches()) {
            return new ParsedPublishedText(
                    matcher.group("ai") != null ? matcher.group("ai").trim() : "",
                    matcher.group("doctor") != null ? matcher.group("doctor").trim() : ""
            );
        }
        return new ParsedPublishedText(resultText.trim(), "");
    }

    public record ParsedPublishedText(String aiReportText, String doctorReportText) {}
}
