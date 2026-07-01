package com.hospital.patient.repository;

import com.hospital.common.constant.BillBizType;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class BillRepository {

    private final JdbcClient jdbcClient;

    public BillRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insertBill(Long patientId, Long registerId, String bizType, Long bizId,
                           String billTitle, BigDecimal amount) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO bill (patient_id, register_id, biz_type, biz_id, bill_title, amount, status)
                        VALUES (:patientId, :registerId, :bizType, :bizId, :billTitle, :amount, 0)
                        """)
                .param("patientId", patientId)
                .param("registerId", registerId)
                .param("bizType", bizType)
                .param("bizId", bizId)
                .param("billTitle", billTitle)
                .param("amount", amount)
                .update(keyHolder, "id");
        return keyHolder.getKey().longValue();
    }

    public List<Map<String, Object>> findPendingByPatient(Long patientId) {
        return jdbcClient.sql("""
                        SELECT id, biz_type, biz_id, bill_title, amount, status, register_id, create_time
                        FROM bill
                        WHERE patient_id = :patientId AND status = 0
                        ORDER BY create_time DESC
                        """)
                .param("patientId", patientId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("bizType", rs.getString("biz_type"));
                    row.put("bizId", rs.getLong("biz_id"));
                    row.put("billTitle", rs.getString("bill_title"));
                    row.put("amount", rs.getBigDecimal("amount"));
                    row.put("status", rs.getInt("status"));
                    row.put("registerId", rs.getObject("register_id", Long.class));
                    row.put("createTime", rs.getObject("create_time", OffsetDateTime.class));
                    return row;
                })
                .list();
    }

    public List<Map<String, Object>> findByIdsForPatient(List<Long> billIds, Long patientId) {
        if (billIds == null || billIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT id, patient_id, register_id, biz_type, biz_id, bill_title, amount, status
                        FROM bill
                        WHERE id IN (:ids) AND patient_id = :patientId
                        """)
                .param("ids", billIds)
                .param("patientId", patientId)
                .query((rs, rowNum) -> mapBillRow(rs))
                .list();
    }

    public List<Map<String, Object>> findByIds(List<Long> billIds) {
        if (billIds == null || billIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT id, patient_id, register_id, biz_type, biz_id, bill_title, amount, status
                        FROM bill
                        WHERE id IN (:ids)
                        """)
                .param("ids", billIds)
                .query((rs, rowNum) -> mapBillRow(rs))
                .list();
    }

    public void markPaid(Long billId) {
        jdbcClient.sql("""
                        UPDATE bill SET status = 1, paid_time = :now, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", billId)
                .param("now", OffsetDateTime.now())
                .update();
    }

    public Optional<Map<String, Object>> findByIdForUpdate(Long billId) {
        return jdbcClient.sql("""
                        SELECT id, patient_id, register_id, biz_type, biz_id, bill_title, amount, status
                        FROM bill
                        WHERE id = :id
                        FOR UPDATE
                        """)
                .param("id", billId)
                .query((rs, rowNum) -> mapBillRow(rs))
                .optional();
    }

    public Optional<Map<String, Object>> findPaidByBiz(String bizType, Long bizId) {
        return jdbcClient.sql("""
                        SELECT id, patient_id, register_id, biz_type, biz_id, bill_title, amount, status
                        FROM bill
                        WHERE biz_type = :bizType AND biz_id = :bizId AND status = 1
                        """)
                .param("bizType", bizType)
                .param("bizId", bizId)
                .query((rs, rowNum) -> mapBillRow(rs))
                .optional();
    }

    public Optional<Map<String, Object>> findByBiz(String bizType, Long bizId) {
        return jdbcClient.sql("""
                        SELECT id, patient_id, register_id, biz_type, biz_id, bill_title, amount, status
                        FROM bill
                        WHERE biz_type = :bizType AND biz_id = :bizId
                        """)
                .param("bizType", bizType)
                .param("bizId", bizId)
                .query((rs, rowNum) -> mapBillRow(rs))
                .optional();
    }

    /** 药师驳回退费后，医生重新提交处方：复用原账单，重置为待支付并更新金额。 */
    public int resetForResubmit(Long billId, String billTitle, BigDecimal amount) {
        return jdbcClient.sql("""
                        UPDATE bill
                        SET status = 0,
                            bill_title = :billTitle,
                            amount = :amount,
                            paid_time = NULL,
                            update_time = NOW()
                        WHERE id = :id AND status = 2
                        """)
                .param("id", billId)
                .param("billTitle", billTitle)
                .param("amount", amount)
                .update();
    }

    public void markRefunded(Long billId) {
        jdbcClient.sql("""
                        UPDATE bill SET status = 2, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", billId)
                .update();
    }

    public List<Map<String, Object>> findByPatientId(Long patientId, Integer status) {
        return jdbcClient.sql("""
                        SELECT id, patient_id, biz_type, biz_id, bill_title, amount, status, register_id, create_time, paid_time
                        FROM bill
                        WHERE patient_id = :patientId
                          AND (CAST(:status AS INTEGER) IS NULL OR status = CAST(:status AS INTEGER))
                        ORDER BY create_time DESC
                        """)
                .param("patientId", patientId)
                .param("status", status)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = mapBillRow(rs);
                    row.put("createTime", rs.getObject("create_time", OffsetDateTime.class));
                    row.put("paidTime", rs.getObject("paid_time", OffsetDateTime.class));
                    return row;
                })
                .list();
    }

    /** 列表展示：将同次挂号的病历本费合并进挂号费行，不单独展示 MEDICAL_BOOK。 */
    public List<Map<String, Object>> findByPatientIdForDisplay(Long patientId, Integer status) {
        return mergeMedicalBookIntoRegister(findByPatientId(patientId, status));
    }

    /** 支付时自动带上同次挂号待支付病历本账单（兼容历史拆单数据）。 */
    public List<Long> expandWithPendingMedicalBookBills(List<Long> billIds) {
        if (billIds == null || billIds.isEmpty()) {
            return List.of();
        }
        Set<Long> expanded = new LinkedHashSet<>(billIds);
        for (Map<String, Object> bill : findByIds(billIds)) {
            if (!BillBizType.REGISTER.equals(bill.get("bizType"))) {
                continue;
            }
            Long registerId = (Long) bill.get("registerId");
            if (registerId == null) {
                continue;
            }
            for (Map<String, Object> pending : findPendingByRegisterId(registerId)) {
                if (BillBizType.MEDICAL_BOOK.equals(pending.get("bizType"))) {
                    expanded.add(((Number) pending.get("id")).longValue());
                }
            }
        }
        return new ArrayList<>(expanded);
    }

    private List<Map<String, Object>> mergeMedicalBookIntoRegister(List<Map<String, Object>> bills) {
        Map<Long, Map<String, Object>> medicalBooksByRegister = new HashMap<>();
        for (Map<String, Object> bill : bills) {
            if (!BillBizType.MEDICAL_BOOK.equals(bill.get("bizType"))) {
                continue;
            }
            Long registerId = (Long) bill.get("registerId");
            if (registerId != null) {
                medicalBooksByRegister.put(registerId, bill);
            }
        }

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> bill : bills) {
            if (BillBizType.MEDICAL_BOOK.equals(bill.get("bizType"))) {
                continue;
            }
            if (!BillBizType.REGISTER.equals(bill.get("bizType"))) {
                merged.add(bill);
                continue;
            }
            Long registerId = (Long) bill.get("registerId");
            Map<String, Object> medicalBook = registerId != null ? medicalBooksByRegister.get(registerId) : null;
            if (medicalBook == null) {
                merged.add(bill);
                continue;
            }
            Map<String, Object> row = new HashMap<>(bill);
            BigDecimal combined = ((BigDecimal) bill.get("amount")).add((BigDecimal) medicalBook.get("amount"));
            row.put("amount", combined);
            row.put("billTitle", "挂号费（含病历本）");
            merged.add(row);
        }
        return merged;
    }

    public List<Map<String, Object>> findPendingByRegisterId(Long registerId) {
        return jdbcClient.sql("""
                        SELECT id, patient_id, register_id, biz_type, biz_id, bill_title, amount, status
                        FROM bill
                        WHERE register_id = :registerId AND status = 0
                        ORDER BY id
                        """)
                .param("registerId", registerId)
                .query((rs, rowNum) -> mapBillRow(rs))
                .list();
    }

    public void markVoid(Long billId) {
        jdbcClient.sql("""
                        UPDATE bill SET status = 9, update_time = NOW()
                        WHERE id = :id AND status = 0
                        """)
                .param("id", billId)
                .update();
    }

    private Map<String, Object> mapBillRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("patientId", rs.getLong("patient_id"));
        row.put("registerId", rs.getObject("register_id", Long.class));
        row.put("bizType", rs.getString("biz_type"));
        row.put("bizId", rs.getLong("biz_id"));
        row.put("billTitle", rs.getString("bill_title"));
        row.put("amount", rs.getBigDecimal("amount"));
        row.put("status", rs.getInt("status"));
        return row;
    }
}
