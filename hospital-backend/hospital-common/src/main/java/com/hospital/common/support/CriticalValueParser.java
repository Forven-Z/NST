package com.hospital.common.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CriticalValueParser {

    // 指标名  数值  单位  参考 lo-hi  [↑/↓]
    private static final Pattern LINE = Pattern.compile(
            "^\\s*(?<name>.+?)\\s{2,}(?<value>[\\d.]+)\\s+(?<unit>[^\\s]+(?:\\s*[^\\s]+)?)\\s+参考\\s+(?<ref>[^↑↓\\s]+(?:\\s*[^↑↓\\s]+)?)\\s*(?<arrow>[↑↓])?\\s*$");

    private CriticalValueParser() {}

    public record CriticalItem(String name, String value, String unit, String refRange, String flag) {}

    public static List<CriticalItem> parse(String instrumentData) {
        List<CriticalItem> items = new ArrayList<>();
        if (instrumentData == null || instrumentData.isBlank()) {
            return items;
        }
        for (String rawLine : instrumentData.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("【") || line.startsWith("采样")) {
                continue;
            }
            Matcher m = LINE.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String ref = m.group("ref").trim();
            double value = Double.parseDouble(m.group("value"));
            String flag = evaluate(ref, value);
            if (flag != null) {
                items.add(new CriticalItem(
                        m.group("name").trim(),
                        m.group("value"),
                        m.group("unit").trim(),
                        ref,
                        flag
                ));
            }
        }
        return items;
    }

    private static String evaluate(String ref, double value) {
        if (ref.contains("-")) {
            String[] parts = ref.split("-");
            if (parts.length != 2) return null;
            double lo = Double.parseDouble(parts[0].trim());
            double hi = Double.parseDouble(parts[1].trim());
            if (value < lo) return "LOW";
            if (value > hi) return "HIGH";
            return null;
        }
        if (ref.startsWith("<")) {
            double hi = Double.parseDouble(ref.substring(1).trim());
            return value > hi ? "HIGH" : null;
        }
        return null;
    }
}
