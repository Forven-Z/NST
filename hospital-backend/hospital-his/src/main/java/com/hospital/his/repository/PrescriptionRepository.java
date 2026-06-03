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
public class PrescriptionRepository {

    private final JdbcClient jdbcClient;

    public PrescriptionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insertPrescription(Long registerId, Long patientId, Long doctorId, String prescriptionNo,
                                   BigDecimal totalAmount, int status, String remark) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO prescription (register_id, patient_id, doctor_id, prescription_no,
                                                  total_amount, status, order_time, remark)
                        VALUES (:registerId, :patientId, :doctorId, :prescriptionNo,
                                :totalAmount, :status, :orderTime, :remark)
                        """)
                .param("registerId", registerId)
                .param("patientId", patientId)
                .param("doctorId", doctorId)
                .param("prescriptionNo", prescriptionNo)
                .param("totalAmount", totalAmount)
                .param("status", status)
                .param("orderTime", OffsetDateTime.now())
                .param("remark", remark)
                .update(keyHolder, "id");
        return keyHolder.getKey().longValue();
    }

    public void insertItem(Long prescriptionId, Long drugId, String drugCode, String drugName,
                           String specification, BigDecimal unitPrice, BigDecimal quantity, BigDecimal amount,
                           String usageMethod, String dosage, String frequency, Integer days, String entrust,
                           int sortNo) {
        jdbcClient.sql("""
                        INSERT INTO prescription_item (prescription_id, drug_id, drug_code, drug_name, specification,
                                                       unit_price, quantity, amount, usage_method, dosage,
                                                       frequency, days, entrust, sort_no)
                        VALUES (:prescriptionId, :drugId, :drugCode, :drugName, :specification,
                                :unitPrice, :quantity, :amount, :usageMethod, :dosage,
                                :frequency, :days, :entrust, :sortNo)
                        """)
                .param("prescriptionId", prescriptionId)
                .param("drugId", drugId)
                .param("drugCode", drugCode)
                .param("drugName", drugName)
                .param("specification", specification)
                .param("unitPrice", unitPrice)
                .param("quantity", quantity)
                .param("amount", amount)
                .param("usageMethod", usageMethod)
                .param("dosage", dosage)
                .param("frequency", frequency)
                .param("days", days)
                .param("entrust", entrust)
                .param("sortNo", sortNo)
                .update();
    }

    public void updateStatus(Long prescriptionId, int status) {
        jdbcClient.sql("""
                        UPDATE prescription SET status = :status, update_time = NOW()
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", prescriptionId)
                .param("status", status)
                .update();
    }

    public Optional<Map<String, Object>> findByIdForUpdate(Long prescriptionId) {
        return jdbcClient.sql("""
                        SELECT id, register_id, patient_id, doctor_id, prescription_no, total_amount, status
                        FROM prescription
                        WHERE id = :id AND delmark = 0
                        FOR UPDATE
                        """)
                .param("id", prescriptionId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("prescriptionId", rs.getLong("id"));
                    row.put("registerId", rs.getLong("register_id"));
                    row.put("patientId", rs.getLong("patient_id"));
                    row.put("doctorId", rs.getLong("doctor_id"));
                    row.put("prescriptionNo", rs.getString("prescription_no"));
                    row.put("totalAmount", rs.getBigDecimal("total_amount"));
                    row.put("status", rs.getInt("status"));
                    return row;
                })
                .optional();
    }

    public List<Map<String, Object>> findPending(Integer status, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT p.id AS prescription_id,
                               p.prescription_no,
                               p.register_id,
                               p.total_amount,
                               p.status,
                               p.order_time,
                               pt.medical_record_no,
                               pt.real_name AS patient_name,
                               e.real_name AS doctor_name
                        FROM prescription p
                        JOIN patient pt ON p.patient_id = pt.id
                        JOIN employee e ON p.doctor_id = e.id
                        WHERE p.delmark = 0
                          AND (CAST(:status AS INTEGER) IS NULL OR p.status = CAST(:status AS INTEGER))
                        ORDER BY p.order_time
                        LIMIT :limit OFFSET :offset
                        """)
                .param("status", status)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("prescriptionId", rs.getLong("prescription_id"));
                    row.put("prescriptionNo", rs.getString("prescription_no"));
                    row.put("registerId", rs.getLong("register_id"));
                    row.put("totalAmount", rs.getBigDecimal("total_amount"));
                    row.put("status", rs.getInt("status"));
                    row.put("orderTime", rs.getObject("order_time", OffsetDateTime.class));
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("patientName", rs.getString("patient_name"));
                    row.put("doctorName", rs.getString("doctor_name"));
                    return row;
                })
                .list();
    }

    public List<Map<String, Object>> findItemsByPrescriptionId(Long prescriptionId) {
        return jdbcClient.sql("""
                        SELECT drug_id, drug_name, quantity, unit_price, amount
                        FROM prescription_item
                        WHERE prescription_id = :prescriptionId
                        ORDER BY sort_no, id
                        """)
                .param("prescriptionId", prescriptionId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("drugId", rs.getLong("drug_id"));
                    row.put("drugName", rs.getString("drug_name"));
                    row.put("quantity", rs.getBigDecimal("quantity"));
                    row.put("unitPrice", rs.getBigDecimal("unit_price"));
                    row.put("amount", rs.getBigDecimal("amount"));
                    return row;
                })
                .list();
    }

    public void markDispensed(Long prescriptionId, Long pharmacistId) {
        jdbcClient.sql("""
                        UPDATE prescription
                        SET status = 30, pharmacist_id = :pharmacistId, dispense_time = :now, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", prescriptionId)
                .param("pharmacistId", pharmacistId)
                .param("now", OffsetDateTime.now())
                .update();
    }

    public void markReturned(Long prescriptionId) {
        jdbcClient.sql("""
                        UPDATE prescription SET status = 40, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", prescriptionId)
                .update();
    }
}
