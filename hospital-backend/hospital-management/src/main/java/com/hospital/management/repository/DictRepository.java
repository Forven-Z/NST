package com.hospital.management.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DictRepository {

    private final JdbcClient jdbcClient;

    public DictRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> listDepartments(String keyword, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT id, dept_code, dept_name, dept_type, parent_id, sort_no
                        FROM department
                        WHERE delmark = 0
                          AND (CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                               OR dept_name ILIKE :pattern OR dept_code ILIKE :pattern)
                        ORDER BY sort_no, id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("keyword", keyword)
                .param("pattern", likePattern(keyword))
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("deptCode", rs.getString("dept_code"));
                    row.put("deptName", rs.getString("dept_name"));
                    row.put("deptType", rs.getObject("dept_type", Integer.class));
                    row.put("parentId", rs.getObject("parent_id", Long.class));
                    row.put("sortNo", rs.getObject("sort_no", Integer.class));
                    return row;
                })
                .list();
    }

    public List<Map<String, Object>> listRegistLevels(String keyword, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT id, level_code, level_name, regist_fee
                        FROM regist_level
                        WHERE delmark = 0
                          AND (CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                               OR level_name ILIKE :pattern OR level_code ILIKE :pattern)
                        ORDER BY id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("keyword", keyword)
                .param("pattern", likePattern(keyword))
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("levelCode", rs.getString("level_code"));
                    row.put("levelName", rs.getString("level_name"));
                    row.put("registFee", rs.getBigDecimal("regist_fee"));
                    return row;
                })
                .list();
    }

    public List<Map<String, Object>> listSettleCategories(String keyword, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT id, category_code, category_name
                        FROM settle_category
                        WHERE delmark = 0
                          AND (CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                               OR category_name ILIKE :pattern OR category_code ILIKE :pattern)
                        ORDER BY id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("keyword", keyword)
                .param("pattern", likePattern(keyword))
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("categoryCode", rs.getString("category_code"));
                    row.put("categoryName", rs.getString("category_name"));
                    return row;
                })
                .list();
    }

    public List<Map<String, Object>> listMedicalTechnologies(String keyword, String techType, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT id, item_code, item_name, tech_type, price, dept_id
                        FROM medical_technology
                        WHERE delmark = 0
                          AND (CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                               OR item_name ILIKE :pattern OR item_code ILIKE :pattern)
                          AND (CAST(:techType AS VARCHAR) IS NULL OR tech_type = CAST(:techType AS VARCHAR))
                        ORDER BY tech_type, id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("keyword", keyword)
                .param("pattern", likePattern(keyword))
                .param("techType", techType)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("itemCode", rs.getString("item_code"));
                    row.put("itemName", rs.getString("item_name"));
                    row.put("techType", rs.getString("tech_type"));
                    row.put("price", rs.getBigDecimal("price"));
                    row.put("deptId", rs.getObject("dept_id", Long.class));
                    return row;
                })
                .list();
    }

    public List<Map<String, Object>> listDrugs(String keyword, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT id, drug_code, drug_name, specification, unit, retail_price, stock_qty
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
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("drugCode", rs.getString("drug_code"));
                    row.put("drugName", rs.getString("drug_name"));
                    row.put("specification", rs.getString("specification"));
                    row.put("unit", rs.getString("unit"));
                    row.put("retailPrice", rs.getBigDecimal("retail_price"));
                    row.put("stockQty", rs.getObject("stock_qty", Integer.class));
                    return row;
                })
                .list();
    }

    public List<Map<String, Object>> listDiseases(String keyword, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT id, disease_code, disease_name, disease_category
                        FROM disease
                        WHERE delmark = 0
                          AND (CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                               OR disease_name ILIKE :pattern OR disease_code ILIKE :pattern)
                        ORDER BY id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("keyword", keyword)
                .param("pattern", likePattern(keyword))
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("diseaseCode", rs.getString("disease_code"));
                    row.put("diseaseName", rs.getString("disease_name"));
                    row.put("diseaseCategory", rs.getString("disease_category"));
                    return row;
                })
                .list();
    }

    public Optional<Map<String, Object>> findDepartmentById(Long id) {
        return findById("department", id, (rs, rowNum) -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("deptCode", rs.getString("dept_code"));
            row.put("deptName", rs.getString("dept_name"));
            return row;
        }, "dept_code", "dept_name");
    }

    private <T> Optional<T> findById(String table, Long id, org.springframework.jdbc.core.RowMapper<T> mapper,
                                     String... columns) {
        String cols = columns.length > 0 ? "id, " + String.join(", ", columns) : "id";
        return jdbcClient.sql("SELECT " + cols + " FROM " + table + " WHERE id = :id AND delmark = 0")
                .param("id", id)
                .query(mapper)
                .optional();
    }

    private String likePattern(String keyword) {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    }
}
