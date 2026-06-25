package com.hospital.his.repository;

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
