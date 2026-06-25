package com.hospital.common.support;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检验报告视图拼接（LIS / 门诊医生 / 患者端共用，唯一拼接入口）。
 */
public final class LabReportComposer {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private LabReportComposer() {}

    public static Map<String, Object> composeView(
            Map<String, Object> context,
            List<Map<String, Object>> items,
            String aiReportText,
            String doctorReportText,
            String aiReportStatus) {

        Long requestId = toLong(context.get("inspectionRequestId"));
        String itemName = stringVal(context.get("itemName"));
        String resultText = stringVal(context.get("resultText"));
        int status = toInt(context.get("status"));

        String ai = aiReportText != null ? aiReportText.trim() : "";
        String doctor = doctorReportText != null ? doctorReportText.trim() : "";
        if (ai.isBlank() && doctor.isBlank() && !resultText.isBlank()) {
            var parsed = parsePublishedText(resultText);
            ai = parsed.aiReportText();
            doctor = parsed.doctorReportText();
        }

        String resolvedAiStatus = aiReportStatus;
        if (resolvedAiStatus == null || resolvedAiStatus.isBlank()) {
            resolvedAiStatus = ai.isBlank() ? "PENDING" : "READY";
        }

        List<Map<String, Object>> safeItems = items != null ? items : List.of();
        if (safeItems.isEmpty() && !resultText.isBlank()) {
            safeItems = LabReportItemTemplates.defaultItemsFor(itemName);
        }

        OffsetDateTime executeTime = (OffsetDateTime) context.get("executeTime");
        OffsetDateTime resultTime = (OffsetDateTime) context.get("resultTime");
        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("patientName", stringVal(context.get("patientName")));
        header.put("genderLabel", genderLabel(context.get("gender")));
        header.put("ageLabel", ageLabel(context.get("age")));
        header.put("medicalRecordNo", stringVal(context.get("medicalRecordNo")));
        header.put("sampleType", LabReportItemTemplates.sampleTypeFor(itemName));
        header.put("sourceLabel", "门诊");
        header.put("department", stringVal(context.get("departmentName")));
        header.put("clinicalDiagnosis", stringVal(context.get("clinicalDiagnosis")));
        header.put("purpose", stringVal(context.get("purpose")));
        header.put("bodyPart", stringVal(context.get("bodyPart")));
        header.put("remark", stringVal(context.get("orderRemark")));

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("aiReportText", ai);
        analysis.put("doctorReportText", doctor);
        analysis.put("aiReportStatus", resolvedAiStatus);

        Map<String, Object> footer = new LinkedHashMap<>();
        footer.put("executeTime", formatTime(executeTime != null ? executeTime : now));
        footer.put("reportTime", formatTime(resultTime != null ? resultTime : now));
        footer.put("orderingDoctorName", stringVal(context.get("orderingDoctorName")));
        footer.put("testerName", stringVal(context.get("testerName")));
        footer.put("reporterName", stringVal(context.get("reporterName")));
        footer.put("reviewerName", formatReviewerName(context.get("reviewerName")));

        String composedText = composeResultText(safeItems, ai, doctor);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("reportType", "lab");
        view.put("inspectionRequestId", requestId);
        view.put("reportTitle", itemName);
        view.put("reportNo", buildReportNo(requestId, resultTime));
        view.put("status", status);
        view.put("itemName", itemName);
        view.put("header", header);
        view.put("items", safeItems);
        view.put("analysis", analysis);
        view.put("footer", footer);
        view.put("resultText", composedText);

        // 兼容旧字段（RegisterOrdersPanel 过渡期）
        view.put("aiReportText", ai);
        view.put("doctorReportText", doctor);
        view.put("aiReportStatus", resolvedAiStatus);
        view.put("reportTime", footer.get("reportTime"));
        view.put("resultTime", resultTime);
        return view;
    }

    private static final String MARKER_AI = "【诊断分析】";
    private static final String MARKER_DOCTOR = "【医师意见】";

    public record ParsedRecord(String aiReportText, String doctorReportText) {}

    /**
     * 从持久化的 result_text 还原 AI 分析与医师意见。
     * 支持结构化格式（【检验结果】/【诊断分析】/【医师意见】）及旧版 AI：/医师： 格式。
     */
    public static ParsedRecord parsePublishedText(String resultText) {
        if (resultText == null || resultText.isBlank()) {
            return new ParsedRecord("", "");
        }
        String trimmed = resultText.trim();
        if (trimmed.contains(MARKER_AI) || trimmed.contains(MARKER_DOCTOR)) {
            return new ParsedRecord(
                    extractSection(trimmed, MARKER_AI, MARKER_DOCTOR),
                    extractSection(trimmed, MARKER_DOCTOR, null)
            );
        }
        var legacy = MedTechReportSupport.parsePublishedText(trimmed);
        return new ParsedRecord(legacy.aiReportText(), legacy.doctorReportText());
    }

    private static String extractSection(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        if (start < 0) {
            return "";
        }
        start += startMarker.length();
        while (start < text.length() && (text.charAt(start) == '\n' || text.charAt(start) == '\r')) {
            start++;
        }
        if (endMarker == null) {
            return text.substring(start).trim();
        }
        int end = text.indexOf(endMarker, start);
        if (end < 0) {
            return text.substring(start).trim();
        }
        return text.substring(start, end).trim();
    }

    public static String composeResultText(List<Map<String, Object>> items, String aiReportText, String doctorReportText) {
        StringBuilder sb = new StringBuilder();
        if (items != null && !items.isEmpty()) {
            sb.append("【检验结果】\n");
            for (Map<String, Object> item : items) {
                sb.append(formatItemLine(item)).append('\n');
            }
        }
        String ai = aiReportText != null ? aiReportText.trim() : "";
        String doctor = doctorReportText != null ? doctorReportText.trim() : "";
        if (!ai.isBlank()) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append("【诊断分析】\n").append(ai);
        }
        if (!doctor.isBlank()) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append("【医师意见】\n").append(doctor);
        }
        return sb.toString().trim();
    }

    public static String summarize(List<Map<String, Object>> items, String aiReportText, String doctorReportText) {
        String text = composeResultText(items, aiReportText, doctorReportText);
        if (text.isBlank()) {
            return "报告已出，点击查看详情";
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }

    public static String generateAiReportStub(String itemName, List<Map<String, Object>> items) {
        List<String> abnormal = new ArrayList<>();
        if (items != null) {
            for (Map<String, Object> item : items) {
                String flag = stringVal(item.get("flag"));
                if ("H".equalsIgnoreCase(flag) || "L".equalsIgnoreCase(flag)) {
                    abnormal.add(stringVal(item.get("name")) + " "
                            + stringVal(item.get("result")) + stringVal(item.get("unit"))
                            + ("H".equalsIgnoreCase(flag) ? "↑" : "↓"));
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【AI 智能检验报告 · ").append(itemName != null ? itemName : "检验").append("】\n");
        if (abnormal.isEmpty()) {
            sb.append("综合检验指标：各项目均在参考范围内或未见明显异常模式。\n");
            sb.append("AI 提示：建议结合临床症状继续观察，请检验师审核确认。");
        } else {
            sb.append("异常项目：").append(String.join("；", abnormal)).append("。\n");
            sb.append("AI 提示：存在偏离参考范围指标，建议结合临床排查感染、代谢或炎症可能，请检验师审核确认。");
        }
        return sb.toString();
    }

    private static String formatItemLine(Map<String, Object> item) {
        String name = stringVal(item.get("name"));
        String result = stringVal(item.get("result"));
        String unit = stringVal(item.get("unit"));
        String ref = stringVal(item.get("refRange"));
        String flag = stringVal(item.get("flag"));
        String arrow = "H".equalsIgnoreCase(flag) ? " ↑" : "L".equalsIgnoreCase(flag) ? " ↓" : "";
        String unitPart = unit.isBlank() ? "" : " " + unit;
        String refPart = ref.isBlank() ? "" : "  参考 " + ref;
        return name + "  " + result + unitPart + refPart + arrow;
    }

    private static String buildReportNo(Long requestId, OffsetDateTime resultTime) {
        long suffix = requestId != null ? requestId : 0L;
        return "LAB-" + String.format("%05d", suffix);
    }

    private static String formatReviewerName(Object reviewerName) {
        String name = stringVal(reviewerName);
        return name.isBlank() ? "待审核" : name;
    }

    private static String genderLabel(Object gender) {
        if (gender == null) return "—";
        int g = toInt(gender);
        return g == 1 ? "男" : g == 2 ? "女" : "—";
    }

    private static String ageLabel(Object age) {
        if (age == null) return "—";
        return toInt(age) + "岁";
    }

    private static String formatTime(OffsetDateTime time) {
        return time != null ? time.format(DISPLAY_TIME) : "—";
    }

    private static String stringVal(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
