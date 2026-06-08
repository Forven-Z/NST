package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class DrugRepository {

    private final JdbcClient jdbcClient;

    public DrugRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
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
}
