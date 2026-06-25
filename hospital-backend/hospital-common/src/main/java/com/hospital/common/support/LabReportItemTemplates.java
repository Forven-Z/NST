package com.hospital.common.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LIS 仪器结果 STUB 模板（按检验项目名称；真实环境由仪器接口写入 inspection_result_item）。
 */
public final class LabReportItemTemplates {

    private LabReportItemTemplates() {}

    public record TemplateItem(String code, String name, String result, String unit, String refRange, String flag) {}

    private static final Map<String, List<TemplateItem>> BY_ITEM_NAME = new LinkedHashMap<>();
    private static final Map<String, String> SAMPLE_TYPE_BY_ITEM = new LinkedHashMap<>();

    static {
        SAMPLE_TYPE_BY_ITEM.put("血常规", "全血");
        SAMPLE_TYPE_BY_ITEM.put("C反应蛋白", "全血");
        SAMPLE_TYPE_BY_ITEM.put("降钙素原", "全血");
        SAMPLE_TYPE_BY_ITEM.put("尿常规", "尿液");
        SAMPLE_TYPE_BY_ITEM.put("粪便常规及隐血", "粪便");
        SAMPLE_TYPE_BY_ITEM.put("空腹血糖", "血清");
        SAMPLE_TYPE_BY_ITEM.put("血脂四项", "血清");

        BY_ITEM_NAME.put("血常规", List.of(
                new TemplateItem("WBC", "白细胞", "12.8", "×10⁹/L", "3.5-9.5", "H"),
                new TemplateItem("RBC", "红细胞", "4.65", "×10¹²/L", "4.3-5.8", "N"),
                new TemplateItem("HGB", "血红蛋白", "138", "g/L", "130-175", "N"),
                new TemplateItem("PLT", "血小板", "210", "×10⁹/L", "125-350", "N"),
                new TemplateItem("NEUT%", "中性粒细胞%", "62.0", "%", "40-75", "N"),
                new TemplateItem("LYMPH%", "淋巴细胞%", "28.5", "%", "20-50", "N")
        ));
        BY_ITEM_NAME.put("C反应蛋白", List.of(
                new TemplateItem("CRP", "C反应蛋白", "18.6", "mg/L", "0-8", "H")
        ));
        BY_ITEM_NAME.put("血脂四项", List.of(
                new TemplateItem("TC", "总胆固醇", "5.82", "mmol/L", "3.1-5.7", "H"),
                new TemplateItem("TG", "甘油三酯", "2.15", "mmol/L", "0.45-1.7", "H"),
                new TemplateItem("HDL-C", "高密度脂蛋白", "1.05", "mmol/L", "1.0-1.6", "N"),
                new TemplateItem("LDL-C", "低密度脂蛋白", "3.68", "mmol/L", "0-3.4", "H")
        ));
        BY_ITEM_NAME.put("空腹血糖", List.of(
                new TemplateItem("GLU", "空腹血糖", "6.8", "mmol/L", "3.9-6.1", "H")
        ));
        BY_ITEM_NAME.put("尿常规", List.of(
                new TemplateItem("PRO", "尿蛋白", "阴性", "", "阴性", "N"),
                new TemplateItem("GLU-U", "尿糖", "阴性", "", "阴性", "N"),
                new TemplateItem("WBC-U", "尿白细胞", "0-3", "/HP", "0-5", "N")
        ));
    }

    public static String sampleTypeFor(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return "标本";
        }
        return SAMPLE_TYPE_BY_ITEM.getOrDefault(itemName.trim(), "标本");
    }

    public static List<Map<String, Object>> defaultItemsFor(String itemName) {
        List<TemplateItem> templates = BY_ITEM_NAME.getOrDefault(
                itemName != null ? itemName.trim() : "",
                List.of(
                        new TemplateItem("ITEM1", "检验项目A", "—", "", "—", ""),
                        new TemplateItem("ITEM2", "检验项目B", "—", "", "—", "")
                ));
        List<Map<String, Object>> items = new ArrayList<>();
        int order = 0;
        for (TemplateItem t : templates) {
            items.add(toItemMap(t, order++));
        }
        return items;
    }

    public static Map<String, Object> toItemMap(TemplateItem item, int sortOrder) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sortOrder", sortOrder);
        row.put("code", item.code());
        row.put("name", item.name());
        row.put("result", item.result());
        row.put("unit", item.unit());
        row.put("refRange", item.refRange());
        row.put("flag", item.flag());
        return row;
    }
}
