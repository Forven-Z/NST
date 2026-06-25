package com.hospital.lis.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class InspectionResultItemRepository {

    private final JdbcClient jdbcClient;

    public InspectionResultItemRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public int countByRequestId(Long inspectionRequestId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*) FROM inspection_result_item
                        WHERE inspection_request_id = :requestId
                        """)
                .param("requestId", inspectionRequestId)
                .query(Integer.class)
                .single();
        return count != null ? count : 0;
    }

    public void deleteByRequestId(Long inspectionRequestId) {
        jdbcClient.sql("""
                        DELETE FROM inspection_result_item
                        WHERE inspection_request_id = :requestId
                        """)
                .param("requestId", inspectionRequestId)
                .update();
    }

    public void insertItems(Long inspectionRequestId, List<Map<String, Object>> items) {
        int order = 0;
        for (Map<String, Object> item : items) {
            jdbcClient.sql("""
                            INSERT INTO inspection_result_item
                                (inspection_request_id, sort_order, item_code, item_name,
                                 result_value, unit, ref_range, abnormal_flag)
                            VALUES (:requestId, :sortOrder, :code, :name,
                                    :result, :unit, :refRange, :flag)
                            """)
                    .param("requestId", inspectionRequestId)
                    .param("sortOrder", item.get("sortOrder") != null ? item.get("sortOrder") : order)
                    .param("code", item.get("code"))
                    .param("name", item.get("name"))
                    .param("result", item.get("result"))
                    .param("unit", item.get("unit"))
                    .param("refRange", item.get("refRange"))
                    .param("flag", item.get("flag"))
                    .update();
            order++;
        }
    }

    public List<Map<String, Object>> findByRequestId(Long inspectionRequestId) {
        return jdbcClient.sql("""
                        SELECT sort_order, item_code, item_name, result_value, unit, ref_range, abnormal_flag
                        FROM inspection_result_item
                        WHERE inspection_request_id = :requestId
                        ORDER BY sort_order, id
                        """)
                .param("requestId", inspectionRequestId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("sortOrder", rs.getInt("sort_order"));
                    row.put("code", rs.getString("item_code"));
                    row.put("name", rs.getString("item_name"));
                    row.put("result", rs.getString("result_value"));
                    row.put("unit", rs.getString("unit"));
                    row.put("refRange", rs.getString("ref_range"));
                    row.put("flag", rs.getString("abnormal_flag"));
                    return row;
                })
                .list();
    }
}
