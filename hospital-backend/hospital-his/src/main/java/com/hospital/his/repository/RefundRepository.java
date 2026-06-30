package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class RefundRepository {

    private final JdbcClient jdbcClient;

    public RefundRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insertRefund(Long paymentId, Long billId, Long patientId,
                             BigDecimal refundAmount, String channel, Long operatorId, String reason) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO refund_record (payment_id, bill_id, patient_id,
                                                   refund_amount, channel, status, operator_id,
                                                   refund_time, reason)
                        VALUES (:paymentId, :billId, :patientId,
                                :refundAmount, :channel, 1, :operatorId,
                                :now, :reason)
                        """)
                .param("paymentId", paymentId)
                .param("billId", billId)
                .param("patientId", patientId)
                .param("refundAmount", refundAmount)
                .param("channel", channel)
                .param("operatorId", operatorId)
                .param("now", OffsetDateTime.now())
                .param("reason", reason)
                .update(keyHolder, "id");
        return keyHolder.getKey().longValue();
    }

    public List<Map<String, Object>> findByPatientId(Long patientId, Long registerId, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT rr.id AS refund_id,
                               rr.payment_id,
                               rr.bill_id,
                               rr.patient_id,
                               rr.refund_amount,
                               rr.channel,
                               rr.refund_time,
                               rr.reason,
                               b.bill_title,
                               b.register_id
                        FROM refund_record rr
                        LEFT JOIN bill b ON b.id = rr.bill_id
                        WHERE rr.patient_id = :patientId
                          AND rr.status = 1
                          AND (
                              CAST(:registerId AS BIGINT) IS NULL
                              OR b.register_id = CAST(:registerId AS BIGINT)
                          )
                        ORDER BY rr.refund_time DESC, rr.id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("patientId", patientId)
                .param("registerId", registerId)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("refundId", rs.getLong("refund_id"));
                    row.put("paymentId", rs.getObject("payment_id", Long.class));
                    row.put("billId", rs.getObject("bill_id", Long.class));
                    row.put("patientId", rs.getLong("patient_id"));
                    row.put("refundAmount", rs.getBigDecimal("refund_amount"));
                    row.put("channel", rs.getString("channel"));
                    row.put("refundTime", rs.getObject("refund_time", OffsetDateTime.class));
                    row.put("reason", rs.getString("reason"));
                    row.put("billTitle", rs.getString("bill_title"));
                    row.put("registerId", rs.getObject("register_id", Long.class));
                    return row;
                })
                .list();
    }

    public List<Map<String, Object>> summarizeByOperator(Long operatorId, OffsetDateTime start, OffsetDateTime end) {
        return jdbcClient.sql("""
                        SELECT channel,
                               COUNT(*)::int AS count,
                               COALESCE(SUM(refund_amount), 0) AS total_amount
                        FROM refund_record
                        WHERE operator_id = :operatorId
                          AND status = 1
                          AND refund_time >= :start
                          AND refund_time < :end
                        GROUP BY channel
                        ORDER BY channel
                        """)
                .param("operatorId", operatorId)
                .param("start", start)
                .param("end", end)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("channel", rs.getString("channel"));
                    row.put("count", rs.getInt("count"));
                    row.put("totalAmount", rs.getBigDecimal("total_amount"));
                    return row;
                })
                .list();
    }
}
