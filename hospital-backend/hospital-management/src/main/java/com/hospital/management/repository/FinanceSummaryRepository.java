package com.hospital.management.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class FinanceSummaryRepository {

    private final JdbcClient jdbcClient;

    public FinanceSummaryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> summarizePayments(OffsetDateTime start, OffsetDateTime end) {
        return jdbcClient.sql("""
                        SELECT channel,
                               COUNT(*)::int AS count,
                               COALESCE(SUM(total_amount), 0) AS total_amount
                        FROM payment_record
                        WHERE status = 1
                          AND pay_time >= :start
                          AND pay_time < :end
                        GROUP BY channel
                        ORDER BY channel
                        """)
                .param("start", start)
                .param("end", end)
                .query((rs, rowNum) -> mapChannelRow(rs))
                .list();
    }

    public List<Map<String, Object>> summarizeRefunds(OffsetDateTime start, OffsetDateTime end) {
        return jdbcClient.sql("""
                        SELECT channel,
                               COUNT(*)::int AS count,
                               COALESCE(SUM(refund_amount), 0) AS total_amount
                        FROM refund_record
                        WHERE status = 1
                          AND refund_time >= :start
                          AND refund_time < :end
                        GROUP BY channel
                        ORDER BY channel
                        """)
                .param("start", start)
                .param("end", end)
                .query((rs, rowNum) -> mapChannelRow(rs))
                .list();
    }

    public int countPayments(OffsetDateTime start, OffsetDateTime end) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)::int
                        FROM payment_record
                        WHERE status = 1
                          AND pay_time >= :start
                          AND pay_time < :end
                        """)
                .param("start", start)
                .param("end", end)
                .query(Integer.class)
                .single();
        return count != null ? count : 0;
    }

    public int countRefunds(OffsetDateTime start, OffsetDateTime end) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)::int
                        FROM refund_record
                        WHERE status = 1
                          AND refund_time >= :start
                          AND refund_time < :end
                        """)
                .param("start", start)
                .param("end", end)
                .query(Integer.class)
                .single();
        return count != null ? count : 0;
    }

    public BigDecimal sumPaymentAmount(OffsetDateTime start, OffsetDateTime end) {
        BigDecimal sum = jdbcClient.sql("""
                        SELECT COALESCE(SUM(total_amount), 0)
                        FROM payment_record
                        WHERE status = 1
                          AND pay_time >= :start
                          AND pay_time < :end
                        """)
                .param("start", start)
                .param("end", end)
                .query(BigDecimal.class)
                .single();
        return sum != null ? sum : BigDecimal.ZERO;
    }

    public BigDecimal sumRefundAmount(OffsetDateTime start, OffsetDateTime end) {
        BigDecimal sum = jdbcClient.sql("""
                        SELECT COALESCE(SUM(refund_amount), 0)
                        FROM refund_record
                        WHERE status = 1
                          AND refund_time >= :start
                          AND refund_time < :end
                        """)
                .param("start", start)
                .param("end", end)
                .query(BigDecimal.class)
                .single();
        return sum != null ? sum : BigDecimal.ZERO;
    }

    private Map<String, Object> mapChannelRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("channel", rs.getString("channel"));
        row.put("count", rs.getInt("count"));
        row.put("totalAmount", rs.getBigDecimal("total_amount"));
        return row;
    }
}
