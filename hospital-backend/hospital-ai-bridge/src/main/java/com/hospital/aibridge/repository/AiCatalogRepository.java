package com.hospital.aibridge.repository;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AiCatalogRepository {

    private final JdbcClient jdbcClient;

    public AiCatalogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> listMedicalTechnologies(String techType, int limit) {
        try {
            return jdbcClient.sql("""
                            SELECT id, item_code, item_name, tech_type, price, dept_id
                            FROM medical_technology
                            WHERE delmark = 0 AND tech_type = :techType
                            ORDER BY id
                            LIMIT :limit
                            """)
                    .param("techType", techType)
                    .param("limit", limit)
                    .query((rs, rowNum) -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rs.getLong("id"));
                        row.put("medicalTechnologyId", rs.getLong("id"));
                        row.put("itemCode", rs.getString("item_code"));
                        row.put("itemName", rs.getString("item_name"));
                        row.put("techType", rs.getString("tech_type"));
                        row.put("price", rs.getBigDecimal("price"));
                        row.put("deptId", rs.getObject("dept_id", Long.class));
                        row.put("purpose", defaultPurpose(techType));
                        row.put("bodyPart", "");
                        row.put("remark", "");
                        return row;
                    })
                    .list();
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    public List<Map<String, Object>> listDrugs(int limit) {
        try {
            return jdbcClient.sql("""
                            SELECT id, drug_code, drug_name, drug_format, drug_dosage, drug_type,
                                   unit, retail_price, stock_qty
                            FROM drug_info
                            WHERE delmark = 0
                            ORDER BY id
                            LIMIT :limit
                            """)
                    .param("limit", limit)
                    .query((rs, rowNum) -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rs.getLong("id"));
                        row.put("drugId", rs.getLong("id"));
                        row.put("drugCode", rs.getString("drug_code"));
                        row.put("drugName", rs.getString("drug_name"));
                        row.put("drugFormat", rs.getString("drug_format"));
                        row.put("drugDosage", rs.getString("drug_dosage"));
                        row.put("drugType", rs.getString("drug_type"));
                        row.put("unit", rs.getString("unit"));
                        row.put("retailPrice", rs.getBigDecimal("retail_price"));
                        row.put("stockQty", rs.getObject("stock_qty", Integer.class));
                        row.put("quantity", 1);
                        row.put("usageMethod", "Oral");
                        row.put("dosage", rs.getString("drug_dosage"));
                        row.put("frequency", "");
                        row.put("days", 3);
                        row.put("entrust", "Doctor confirmation required");
                        return row;
                    })
                    .list();
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private String defaultPurpose(String techType) {
        return switch (techType) {
            case "CHECK" -> "Support diagnosis with imaging or functional examination";
            case "INSPECTION" -> "Support diagnosis with laboratory evidence";
            case "DISPOSAL" -> "Support treatment with clinically indicated disposal";
            default -> "Support outpatient diagnosis and treatment";
        };
    }
}
