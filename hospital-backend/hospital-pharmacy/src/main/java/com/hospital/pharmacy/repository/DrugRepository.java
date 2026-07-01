package com.hospital.pharmacy.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DrugRepository {

    private final JdbcClient jdbcClient;

    public DrugRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> listDrugs(String keyword, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT id, drug_code, drug_name, drug_format, drug_dosage, drug_type,
                               unit, retail_price, stock_qty
                        FROM drug_info
                        WHERE delmark = 0
                          AND (CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                               OR drug_name ILIKE :pattern OR drug_code ILIKE :pattern)
                        ORDER BY id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("keyword", keyword)
                .param("pattern", likePattern(keyword))
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> mapDrugRow(rs, false))
                .list();
    }

    public Optional<Map<String, Object>> findById(Long drugId) {
        return jdbcClient.sql("""
                        SELECT id, drug_code, drug_name, drug_format, drug_dosage, drug_type,
                               unit, retail_price, stock_qty
                        FROM drug_info
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", drugId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("drugId", rs.getLong("id"));
                    row.put("drugCode", rs.getString("drug_code"));
                    row.put("drugName", rs.getString("drug_name"));
                    row.put("drugFormat", rs.getString("drug_format"));
                    row.put("drugDosage", rs.getString("drug_dosage"));
                    row.put("drugType", rs.getString("drug_type"));
                    row.put("unit", rs.getString("unit"));
                    row.put("retailPrice", rs.getBigDecimal("retail_price"));
                    row.put("stockQty", rs.getObject("stock_qty", Integer.class));
                    return row;
                })
                .optional();
    }

    public Optional<Map<String, Object>> findByIdForUpdate(Long drugId) {
        return jdbcClient.sql("""
                        SELECT id, drug_code, drug_name, drug_format, retail_price, stock_qty
                        FROM drug_info
                        WHERE id = :id AND delmark = 0
                        FOR UPDATE
                        """)
                .param("id", drugId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("drugId", rs.getLong("id"));
                    row.put("drugCode", rs.getString("drug_code"));
                    row.put("drugName", rs.getString("drug_name"));
                    row.put("drugFormat", rs.getString("drug_format"));
                    row.put("retailPrice", rs.getBigDecimal("retail_price"));
                    row.put("stockQty", rs.getObject("stock_qty", Integer.class));
                    return row;
                })
                .optional();
    }

    public void deductStock(Long drugId, BigDecimal quantity) {
        jdbcClient.sql("""
                        UPDATE drug_info
                        SET stock_qty = GREATEST(stock_qty - :qty, 0), update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", drugId)
                .param("qty", quantity.intValue())
                .update();
    }

    public void restoreStock(Long drugId, BigDecimal quantity) {
        jdbcClient.sql("""
                        UPDATE drug_info
                        SET stock_qty = stock_qty + :qty, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", drugId)
                .param("qty", quantity.intValue())
                .update();
    }

    public List<Map<String, Object>> listDrugsForPharmacy(String keyword, boolean includeDisabled, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT id, drug_code, drug_name, drug_format, drug_dosage, drug_type,
                               unit, retail_price, stock_qty, delmark
                        FROM drug_info
                        WHERE (:includeDisabled OR delmark = 0)
                          AND (CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                               OR drug_name ILIKE :pattern OR drug_code ILIKE :pattern)
                        ORDER BY delmark ASC, id ASC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("includeDisabled", includeDisabled)
                .param("keyword", keyword)
                .param("pattern", likePattern(keyword))
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> mapDrugRow(rs, true))
                .list();
    }

    public Optional<Map<String, Object>> findByIdAny(Long drugId) {
        return jdbcClient.sql("""
                        SELECT id, drug_code, drug_name, drug_format, drug_dosage, drug_type,
                               unit, retail_price, stock_qty, delmark
                        FROM drug_info
                        WHERE id = :id
                        """)
                .param("id", drugId)
                .query((rs, rowNum) -> mapDrugRow(rs, true))
                .optional();
    }

    public String nextDrugCode() {
        String last = jdbcClient.sql("""
                        SELECT drug_code FROM drug_info
                        WHERE drug_code LIKE 'DRG-%'
                        ORDER BY id DESC
                        LIMIT 1
                        """)
                .query(String.class)
                .optional()
                .orElse(null);
        int next = 1;
        if (last != null && last.matches("DRG-(\\d+)")) {
            next = Integer.parseInt(last.replaceAll("DRG-(\\d+)", "$1")) + 1;
        }
        return String.format("DRG-%03d", next);
    }

    public long insertDrug(String drugCode, String drugName, String drugFormat, String drugDosage,
                           String drugType, String unit, BigDecimal retailPrice, int stockQty) {
        return jdbcClient.sql("""
                        INSERT INTO drug_info
                            (drug_code, drug_name, drug_format, drug_dosage, drug_type, unit, retail_price, stock_qty)
                        VALUES
                            (:drugCode, :drugName, :drugFormat, :drugDosage, :drugType, :unit, :retailPrice, :stockQty)
                        RETURNING id
                        """)
                .param("drugCode", drugCode)
                .param("drugName", drugName)
                .param("drugFormat", drugFormat)
                .param("drugDosage", drugDosage)
                .param("drugType", drugType)
                .param("unit", unit)
                .param("retailPrice", retailPrice)
                .param("stockQty", stockQty)
                .query(Long.class)
                .single();
    }

    public int updateDrug(Long id, String drugName, String drugFormat, String drugDosage,
                          String drugType, String unit, BigDecimal retailPrice, Integer stockQty) {
        return jdbcClient.sql("""
                        UPDATE drug_info
                        SET drug_name = COALESCE(:drugName, drug_name),
                            drug_format = COALESCE(:drugFormat, drug_format),
                            drug_dosage = COALESCE(:drugDosage, drug_dosage),
                            drug_type = COALESCE(:drugType, drug_type),
                            unit = COALESCE(:unit, unit),
                            retail_price = COALESCE(:retailPrice, retail_price),
                            stock_qty = COALESCE(:stockQty, stock_qty),
                            update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("drugName", drugName)
                .param("drugFormat", drugFormat)
                .param("drugDosage", drugDosage)
                .param("drugType", drugType)
                .param("unit", unit)
                .param("retailPrice", retailPrice)
                .param("stockQty", stockQty)
                .update();
    }

    public int setDelmark(Long id, int delmark) {
        return jdbcClient.sql("""
                        UPDATE drug_info
                        SET delmark = :delmark, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("delmark", delmark)
                .update();
    }

    private Map<String, Object> mapDrugRow(java.sql.ResultSet rs, boolean includeDisabled) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("drugCode", rs.getString("drug_code"));
        row.put("drugName", rs.getString("drug_name"));
        row.put("drugFormat", rs.getString("drug_format"));
        row.put("drugDosage", rs.getString("drug_dosage"));
        row.put("drugType", rs.getString("drug_type"));
        row.put("unit", rs.getString("unit"));
        row.put("retailPrice", rs.getBigDecimal("retail_price"));
        row.put("stockQty", rs.getObject("stock_qty", Integer.class));
        if (includeDisabled) {
            row.put("disabled", rs.getInt("delmark") == 1);
        }
        return row;
    }

    private String likePattern(String keyword) {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    }
}
