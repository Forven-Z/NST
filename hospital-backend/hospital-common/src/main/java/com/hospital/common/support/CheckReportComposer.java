package com.hospital.common.support;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检查报告视图拼接（PACS / 门诊医生 / 患者端共用）。
 */
public final class CheckReportComposer {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Pattern FINDINGS_PREFIX = Pattern.compile(
            "^【检查所见】(?<findings>[\\s\\S]*?)(?:\\nAI：|$)");

    private CheckReportComposer() {}

    public record ParsedRecord(String findingsText, String aiReportText, String doctorReportText) {}

    public static Map<String, Object> composeView(
            Map<String, Object> context,
            String findingsText,
            String aiReportText,
            String doctorReportText,
            String aiReportStatus,
            Map<String, Object> imaging) {

        Long requestId = toLong(context.get("checkRequestId"));
        String itemName = stringVal(context.get("itemName"));
        String stored = stringVal(context.get("resultText"));
        int status = toInt(context.get("status"));

        String findings = findingsText != null ? findingsText.trim() : "";
        String ai = aiReportText != null ? aiReportText.trim() : "";
        String doctor = doctorReportText != null ? doctorReportText.trim() : "";

        if (findings.isBlank() && ai.isBlank() && doctor.isBlank() && !stored.isBlank()) {
            ParsedRecord parsed = parsePublishedText(stored);
            findings = parsed.findingsText();
            ai = parsed.aiReportText();
            doctor = parsed.doctorReportText();
        }

        String resolvedAiStatus = aiReportStatus;
        if (resolvedAiStatus == null || resolvedAiStatus.isBlank()) {
            resolvedAiStatus = ai.isBlank() ? "PENDING" : "READY";
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
        header.put("bodyPart", stringVal(context.get("bodyPart")));
        header.put("examDate", formatTime(executeTime != null ? executeTime : now));
        header.put("purpose", stringVal(context.get("purpose")));
        header.put("clinicalDiagnosis", stringVal(context.get("clinicalDiagnosis")));
        header.put("orderRemark", stringVal(context.get("orderRemark")));
        header.put("modality", imaging != null ? stringVal(imaging.get("modality")) : "");
        header.put("itemName", itemName);

        Map<String, Object> findingsBlock = new LinkedHashMap<>();
        findingsBlock.put("findingsText", findings);
        findingsBlock.put("instrumentData", stringVal(context.get("instrumentData")));
        if (imaging != null) {
            findingsBlock.put("studyId", imaging.get("studyId"));
            findingsBlock.put("studyStatus", imaging.get("studyStatus"));
            findingsBlock.put("hasImaging", imaging.get("hasImaging"));
            findingsBlock.put("ctPreviewUrl", imaging.get("ctPreviewUrl"));
            findingsBlock.put("maskPreviewUrl", imaging.get("maskPreviewUrl"));
            findingsBlock.put("reportImages", imaging.get("reportImages"));
            findingsBlock.put("snapshotMeta", imaging.get("snapshotMeta"));
        }

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("aiReportText", ai);
        analysis.put("doctorReportText", doctor);
        analysis.put("aiReportStatus", resolvedAiStatus);

        Map<String, Object> footer = new LinkedHashMap<>();
        footer.put("examTime", formatTime(executeTime != null ? executeTime : now));
        footer.put("reportTime", formatTime(resultTime != null ? resultTime : now));
        footer.put("orderingDoctorName", stringVal(context.get("orderingDoctorName")));
        footer.put("executorName", stringVal(context.get("executorName")));
        footer.put("reporterName", stringVal(context.get("reporterName")));
        footer.put("reviewerName", formatReviewerName(context.get("reviewerName")));

        String composedText = composeResultText(findings, ai, doctor);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("reportType", "check");
        view.put("checkRequestId", requestId);
        view.put("reportTitle", itemName);
        view.put("reportNo", buildReportNo(requestId));
        view.put("status", status);
        view.put("itemName", itemName);
        view.put("header", header);
        view.put("findings", findingsBlock);
        view.put("analysis", analysis);
        view.put("footer", footer);
        view.put("resultText", composedText);

        view.put("findingsText", findings);
        view.put("instrumentData", findingsBlock.get("instrumentData"));
        view.put("aiReportText", ai);
        view.put("doctorReportText", doctor);
        view.put("aiReportStatus", resolvedAiStatus);
        view.put("reportTime", footer.get("reportTime"));
        view.put("resultTime", resultTime);
        if (imaging != null) {
            view.put("studyStatus", imaging.get("studyStatus"));
            view.put("studyId", imaging.get("studyId"));
        }
        return view;
    }

    public static String composeResultText(String findingsText, String aiReportText, String doctorReportText) {
        StringBuilder sb = new StringBuilder();
        String findings = findingsText != null ? findingsText.trim() : "";
        if (!findings.isBlank()) {
            sb.append("【检查所见】\n").append(findings);
        }
        String aiDoctor = MedTechReportSupport.composeResultText(aiReportText, doctorReportText);
        if (!aiDoctor.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(aiDoctor);
        }
        return sb.toString().trim();
    }

    public static ParsedRecord parsePublishedText(String resultText) {
        if (resultText == null || resultText.isBlank()) {
            return new ParsedRecord("", "", "");
        }
        String trimmed = resultText.trim();
        String findings = "";
        String remainder = trimmed;

        Matcher findingsMatcher = FINDINGS_PREFIX.matcher(trimmed);
        if (findingsMatcher.find()) {
            findings = findingsMatcher.group("findings") != null
                    ? findingsMatcher.group("findings").trim() : "";
            int aiIdx = trimmed.indexOf("\nAI：");
            remainder = aiIdx >= 0 ? trimmed.substring(aiIdx + 1) : "";
        }

        var parsed = MedTechReportSupport.parsePublishedText(remainder);
        if (findings.isBlank() && parsed.aiReportText().isBlank() && !parsed.doctorReportText().isBlank()) {
            return new ParsedRecord("", "", parsed.doctorReportText());
        }
        if (findings.isBlank() && !parsed.aiReportText().isBlank() && parsed.doctorReportText().isBlank()
                && !remainder.startsWith("AI：")) {
            return new ParsedRecord(parsed.aiReportText(), "", "");
        }
        return new ParsedRecord(findings, parsed.aiReportText(), parsed.doctorReportText());
    }

    public static String generateAiReportStub(String itemName, String findingsText) {
        String base = MedTechReportSupport.aiReportFor(itemName);
        String findings = findingsText != null ? findingsText.trim() : "";
        if (findings.isBlank()) {
            return base;
        }
        return base + "\n\n【基于检查所见归纳】\n"
                + (findings.length() <= 200 ? findings : findings.substring(0, 200) + "…");
    }

    private static String formatReviewerName(Object reviewerName) {
        String name = stringVal(reviewerName);
        return name.isBlank() ? "待审核" : name;
    }

    private static String buildReportNo(Long requestId) {
        long suffix = requestId != null ? requestId : 0L;
        return "CHK-" + String.format("%05d", suffix);
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
