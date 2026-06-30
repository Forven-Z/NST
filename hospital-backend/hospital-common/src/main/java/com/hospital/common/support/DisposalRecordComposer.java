package com.hospital.common.support;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 处置记录视图拼接（无 AI；LIS/门诊/患者端共用）。
 */
public final class DisposalRecordComposer {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Pattern STRUCTURED_TEXT = Pattern.compile(
            "^【处置过程】(?<process>[\\s\\S]*?)(?:\\n【观察与结果】(?<outcome>[\\s\\S]*))?$");

    private DisposalRecordComposer() {}

    public record ParsedRecord(String processText, String outcomeText) {}

    public static Map<String, Object> composeView(Map<String, Object> context, String processText, String outcomeText) {
        Long requestId = toLong(context.get("disposalRequestId"));
        String itemName = stringVal(context.get("itemName"));
        String stored = stringVal(context.get("resultText"));
        int status = toInt(context.get("status"));

        String process = processText != null ? processText.trim() : "";
        String outcome = outcomeText != null ? outcomeText.trim() : "";
        if (process.isBlank() && outcome.isBlank() && !stored.isBlank()) {
            ParsedRecord parsed = parsePublishedText(stored);
            process = parsed.processText();
            outcome = parsed.outcomeText();
        }

        OffsetDateTime executeTime = (OffsetDateTime) context.get("executeTime");
        OffsetDateTime resultTime = (OffsetDateTime) context.get("resultTime");
        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("patientName", stringVal(context.get("patientName")));
        header.put("genderLabel", genderLabel(context.get("gender")));
        header.put("ageLabel", ageLabel(context.get("age")));
        header.put("medicalRecordNo", stringVal(context.get("medicalRecordNo")));
        header.put("department", stringVal(context.get("departmentName")));
        header.put("clinicalDiagnosis", stringVal(context.get("clinicalDiagnosis")));
        header.put("itemName", itemName);
        header.put("purpose", stringVal(context.get("purpose")));
        header.put("bodyPart", stringVal(context.get("bodyPart")));
        header.put("orderRemark", stringVal(context.get("orderRemark")));

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("processText", process);
        record.put("outcomeText", outcome);

        Map<String, Object> footer = new LinkedHashMap<>();
        footer.put("executeTime", formatTime(executeTime != null ? executeTime : now));
        footer.put("recordTime", formatTime(resultTime != null ? resultTime : now));
        footer.put("orderingDoctorName", stringVal(context.get("orderingDoctorName")));
        footer.put("executorName", stringVal(context.get("executorName")));
        footer.put("recorderName", stringVal(context.get("recorderName")));
        footer.put("reviewerName", formatReviewerName(context.get("reviewerName")));

        String composedText = composeResultText(process, outcome);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("reportType", "disposal");
        view.put("disposalRequestId", requestId);
        view.put("reportTitle", itemName);
        view.put("recordNo", buildRecordNo(requestId, resultTime));
        view.put("status", status);
        view.put("itemName", itemName);
        view.put("header", header);
        view.put("record", record);
        view.put("footer", footer);
        view.put("resultText", composedText);
        view.put("reportTime", footer.get("recordTime"));
        view.put("resultTime", resultTime);
        return view;
    }

    public static String composeResultText(String processText, String outcomeText) {
        String process = processText != null ? processText.trim() : "";
        String outcome = outcomeText != null ? outcomeText.trim() : "";
        if (process.isBlank() && outcome.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!process.isBlank()) {
            sb.append("【处置过程】\n").append(process);
        }
        if (!outcome.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("【观察与结果】\n").append(outcome);
        }
        return sb.toString().trim();
    }

    public static String summarize(String processText, String outcomeText) {
        String text = composeResultText(processText, outcomeText);
        if (text.isBlank()) {
            return "处置记录已出，点击查看详情";
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }

    public static ParsedRecord parsePublishedText(String resultText) {
        if (resultText == null || resultText.isBlank()) {
            return new ParsedRecord("", "");
        }
        String trimmed = resultText.trim();
        Matcher structured = STRUCTURED_TEXT.matcher(trimmed);
        if (structured.matches()) {
            return new ParsedRecord(
                    structured.group("process") != null ? structured.group("process").trim() : "",
                    structured.group("outcome") != null ? structured.group("outcome").trim() : ""
            );
        }
        var legacy = MedTechReportSupport.parsePublishedText(trimmed);
        if (!legacy.aiReportText().isBlank() || !legacy.doctorReportText().isBlank()) {
            return new ParsedRecord("", legacy.doctorReportText().isBlank()
                    ? legacy.aiReportText() : legacy.doctorReportText());
        }
        return new ParsedRecord("", trimmed);
    }

    public static String processPlaceholderFor(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return "描述处置步骤、用药/用量、操作方式等…";
        }
        return switch (itemName.trim()) {
            case "洗胃" -> "例：左侧卧位，16Fr 胃管；温盐水 500ml 入/450ml 出；生命体征平稳…";
            case "静脉输液" -> "例：左前臂留置针，生理盐水 500ml 静滴，滴速 40 滴/分，无渗漏…";
            case "雾化吸入" -> "例：布地奈德+沙丁胺醇雾化 15 分钟，SpO₂ 98%，无呛咳…";
            case "导尿" -> "例：常规消毒，14Fr 导尿管顺利置入，引出尿色清，约 300ml…";
            default -> "描述处置步骤、用量、操作方式及患者即时反应…";
        };
    }

    private static String formatReviewerName(Object reviewerName) {
        String name = stringVal(reviewerName);
        return name.isBlank() ? "待审核" : name;
    }

    private static String buildRecordNo(Long requestId, OffsetDateTime resultTime) {
        long suffix = requestId != null ? requestId : 0L;
        return "DIS-" + String.format("%05d", suffix);
    }

    private static String genderLabel(Object gender) {
        if (gender == null) {
            return "—";
        }
        int g = toInt(gender);
        return g == 1 ? "男" : g == 2 ? "女" : "—";
    }

    private static String ageLabel(Object age) {
        if (age == null) {
            return "—";
        }
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
