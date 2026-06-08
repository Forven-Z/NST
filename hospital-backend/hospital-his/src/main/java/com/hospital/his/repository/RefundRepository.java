package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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
}
