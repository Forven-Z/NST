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
import java.util.Optional;

@Repository
public class PaymentRepository {

    private final JdbcClient jdbcClient;

    public PaymentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insertPayment(Long patientId, BigDecimal totalAmount, String channel) {
        return insertPayment(patientId, totalAmount, channel, null, null);
    }

    public long insertPayment(Long patientId, BigDecimal totalAmount, String channel,
                              Long operatorId, String thirdPartyTradeNo) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO payment_record (patient_id, total_amount, channel, status, pay_time,
                                                    operator_id, third_party_trade_no)
                        VALUES (:patientId, :totalAmount, :channel, 1, :now,
                                :operatorId, :thirdPartyTradeNo)
                        """)
                .param("patientId", patientId)
                .param("totalAmount", totalAmount)
                .param("channel", channel)
                .param("now", OffsetDateTime.now())
                .param("operatorId", operatorId)
                .param("thirdPartyTradeNo", thirdPartyTradeNo)
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
                               pr.patient_id, pr.channel, pr.total_amount
                        FROM payment_bill pb
                        JOIN payment_record pr ON pb.payment_id = pr.id
                        WHERE pb.bill_id = :billId
                        """)
                .param("billId", billId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("paymentId", rs.getLong("payment_id"));
                    row.put("billAmount", rs.getBigDecimal("bill_amount"));
                    row.put("patientId", rs.getLong("patient_id"));
                    row.put("channel", rs.getString("channel"));
                    row.put("totalAmount", rs.getBigDecimal("total_amount"));
                    return row;
                })
                .optional();
    }

    public List<Map<String, Object>> findByPatientId(Long patientId, Long registerId, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT pr.id AS payment_id,
                               pr.patient_id,
                               pr.total_amount,
                               pr.channel,
                               pr.status,
                               pr.pay_time
                        FROM payment_record pr
                        WHERE pr.patient_id = :patientId
                          AND pr.status = 1
                          AND (
                              CAST(:registerId AS BIGINT) IS NULL
                              OR EXISTS (
                                  SELECT 1
                                  FROM payment_bill pb
                                  JOIN bill b ON b.id = pb.bill_id
                                  WHERE pb.payment_id = pr.id
                                    AND b.register_id = CAST(:registerId AS BIGINT)
                              )
                          )
                        ORDER BY pr.pay_time DESC, pr.id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("patientId", patientId)
                .param("registerId", registerId)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> mapPaymentRow(rs))
                .list();
    }

    public Optional<Map<String, Object>> findByIdForPatient(Long paymentId, Long patientId) {
        return jdbcClient.sql("""
                        SELECT pr.id AS payment_id,
                               pr.patient_id,
                               pr.total_amount,
                               pr.channel,
                               pr.status,
                               pr.pay_time
                        FROM payment_record pr
                        WHERE pr.id = :paymentId
                          AND pr.patient_id = :patientId
                        """)
                .param("paymentId", paymentId)
                .param("patientId", patientId)
                .query((rs, rowNum) -> mapPaymentRow(rs))
                .optional();
    }

    public List<Map<String, Object>> findBillsByPaymentId(Long paymentId) {
        return jdbcClient.sql("""
                        SELECT b.id AS bill_id,
                               b.bill_title,
                               b.amount,
                               b.biz_type,
                               b.register_id,
                               b.status
                        FROM payment_bill pb
                        JOIN bill b ON b.id = pb.bill_id
                        WHERE pb.payment_id = :paymentId
                        ORDER BY b.id
                        """)
                .param("paymentId", paymentId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("billId", rs.getLong("bill_id"));
                    row.put("billTitle", rs.getString("bill_title"));
                    row.put("amount", rs.getBigDecimal("amount"));
                    row.put("bizType", rs.getString("biz_type"));
                    row.put("registerId", rs.getObject("register_id", Long.class));
                    row.put("status", rs.getInt("status"));
                    return row;
                })
                .list();
    }

    public List<Map<String, Object>> summarizeByOperator(Long operatorId, OffsetDateTime start, OffsetDateTime end) {
        return jdbcClient.sql("""
                        SELECT channel,
                               COUNT(*)::int AS count,
                               COALESCE(SUM(total_amount), 0) AS total_amount
                        FROM payment_record
                        WHERE operator_id = :operatorId
                          AND status = 1
                          AND pay_time >= :start
                          AND pay_time < :end
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

    private Map<String, Object> mapPaymentRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("paymentId", rs.getLong("payment_id"));
        row.put("patientId", rs.getLong("patient_id"));
        row.put("totalAmount", rs.getBigDecimal("total_amount"));
        row.put("channel", rs.getString("channel"));
        row.put("status", rs.getInt("status"));
        row.put("payTime", rs.getObject("pay_time", OffsetDateTime.class));
        return row;
    }
}
