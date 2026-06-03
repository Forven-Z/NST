package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class PaymentRepository {

    private final JdbcClient jdbcClient;

    public PaymentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insertPayment(String paymentNo, Long patientId, BigDecimal totalAmount, String channel) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO payment_record (payment_no, patient_id, total_amount, channel, status, pay_time)
                        VALUES (:paymentNo, :patientId, :totalAmount, :channel, 1, :now)
                        """)
                .param("paymentNo", paymentNo)
                .param("patientId", patientId)
                .param("totalAmount", totalAmount)
                .param("channel", channel)
                .param("now", OffsetDateTime.now())
                .update(keyHolder, "id");
        return keyHolder.getKey().longValue();
    }

    public void linkBill(Long paymentId, Long billId, BigDecimal amount) {
        jdbcClient.sql("""
                        INSERT INTO payment_bill (payment_id, bill_id, amount)
                        VALUES (:paymentId, :billId, :amount)
                        """)
                .param("paymentId", paymentId)
                .param("billId", billId)
                .param("amount", amount)
                .update();
    }

    public Optional<Map<String, Object>> findPaymentLinkByBillId(Long billId) {
        return jdbcClient.sql("""
                        SELECT pb.payment_id, pb.amount AS bill_amount,
                               pr.payment_no, pr.patient_id, pr.channel, pr.total_amount
                        FROM payment_bill pb
                        JOIN payment_record pr ON pb.payment_id = pr.id
                        WHERE pb.bill_id = :billId
                        """)
                .param("billId", billId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("paymentId", rs.getLong("payment_id"));
                    row.put("billAmount", rs.getBigDecimal("bill_amount"));
                    row.put("paymentNo", rs.getString("payment_no"));
                    row.put("patientId", rs.getLong("patient_id"));
                    row.put("channel", rs.getString("channel"));
                    row.put("totalAmount", rs.getBigDecimal("total_amount"));
                    return row;
                })
                .optional();
    }
}
