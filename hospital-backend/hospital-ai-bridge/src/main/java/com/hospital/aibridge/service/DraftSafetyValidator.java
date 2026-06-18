package com.hospital.aibridge.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * 对模型草稿执行确定性校验。任何不在 SQL 候选目录中的 ID 都会被删除。
 */
@Service
public class DraftSafetyValidator {

    public ValidationResult validate(String draftType, List<Map<String, Object>> generated,
                                     List<Map<String, Object>> candidates) {
        String idField = "PRESCRIPTION".equals(draftType) ? "drugId" : "medicalTechnologyId";
        Map<Long, Map<String, Object>> allowed = new LinkedHashMap<>();
        for (Map<String, Object> candidate : candidates == null ? List.<Map<String, Object>>of() : candidates) {
            Long id = number(candidate.get(idField));
            if (id != null && candidateAllowed(draftType, candidate)) {
                allowed.put(id, candidate);
            }
        }

        Set<String> warnings = new LinkedHashSet<>();
        List<Map<String, Object>> safeItems = (generated == null ? List.<Map<String, Object>>of() : generated).stream()
                .filter(item -> {
                    Long id = number(item.get(idField));
                    boolean valid = id != null && allowed.containsKey(id);
                    if (!valid) {
                        warnings.add("已移除不在院内有效目录中的 AI 推荐项目");
                    }
                    return valid;
                })
                .map(item -> mergeCatalogSnapshot(item, allowed.get(number(item.get(idField))), idField))
                .toList();

        if ("PRESCRIPTION".equals(draftType)) {
            warnings.add("处方需由医生复核过敏史、禁忌证、剂量、频次和疗程");
        }
        return new ValidationResult(safeItems, List.copyOf(warnings));
    }

    private boolean candidateAllowed(String draftType, Map<String, Object> candidate) {
        if ("PRESCRIPTION".equals(draftType)) {
            Long stock = number(candidate.get("stockQty"));
            return stock == null || stock > 0;
        }
        Object type = candidate.get("techType");
        return type == null || draftType.equals(String.valueOf(type));
    }

    private Map<String, Object> mergeCatalogSnapshot(Map<String, Object> generated,
                                                      Map<String, Object> catalog,
                                                      String idField) {
        Map<String, Object> result = new LinkedHashMap<>(generated);
        result.put(idField, catalog.get(idField));
        // 名称和目录属性以数据库快照为准，模型只能补充目的、用法等建议字段。
        copyIfPresent(catalog, result, "itemName");
        copyIfPresent(catalog, result, "drugName");
        copyIfPresent(catalog, result, "itemCode");
        copyIfPresent(catalog, result, "drugCode");
        copyIfPresent(catalog, result, "techType");
        copyIfPresent(catalog, result, "drugFormat");
        copyIfPresent(catalog, result, "drugDosage");
        copyIfPresent(catalog, result, "unit");
        return result;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private Long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record ValidationResult(List<Map<String, Object>> items, List<String> warnings) {
    }
}
