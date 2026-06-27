package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    public long insertPrescription(Long registerId, Long patientId, Long doctorId,
                                   BigDecimal totalAmount, int status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO prescription (register_id, patient_id, doctor_id, total_amount, status)
                        VALUES (:registerId, :patientId, :doctorId, :totalAmount, :status)
                        """)
                .param("registerId", registerId)
                .param("patientId", patientId)
                .param("doctorId", doctorId)
                .param("totalAmount", totalAmount)
                .param("status", status)
                .update(keyHolder, "id");
        return keyHolder.getKey().longValue();
    }

    public void insertItem(Long prescriptionId, Long drugId, String drugCode, String drugName,
                           String drugFormat, String drugDosage, String drugType,
                           BigDecimal unitPrice, BigDecimal quantity, BigDecimal amount,
                           String usageMethod, String dosage, String frequency, Integer days, String entrust,
                           int sortNo) {
        jdbcClient.sql("""
                        INSERT INTO prescription_item (prescription_id, drug_id, drug_code, drug_name,
                                                       drug_format, drug_dosage, drug_type,
                                                       unit_price, quantity, amount, usage_method, dosage,
                                                       frequency, days, entrust, sort_no)
                        VALUES (:prescriptionId, :drugId, :drugCode, :drugName,
                                :drugFormat, :drugDosage, :drugType,
                                :unitPrice, :quantity, :amount, :usageMethod, :dosage,
                                :frequency, :days, :entrust, :sortNo)
                        """)
                .param("prescriptionId", prescriptionId)
                .param("drugId", drugId)
                .param("drugCode", drugCode)
                .param("drugName", drugName)
                .param("drugFormat", drugFormat)
                .param("drugDosage", drugDosage)
                .param("drugType", drugType)
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
                        SELECT id, register_id, patient_id, doctor_id, total_amount, status, create_time
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
                    row.put("totalAmount", rs.getBigDecimal("total_amount"));
                    row.put("status", rs.getInt("status"));
                    row.put("createTime", rs.getObject("create_time", OffsetDateTime.class));
                    return row;
                })
                .optional();
    }

    public List<Map<String, Object>> findPending(Integer status, int offset, int limit) {
        List<Map<String, Object>> list = jdbcClient.sql("""
                        SELECT p.id AS prescription_id,
                               p.register_id,
                               p.total_amount,
                               p.status,
                               p.create_time,
                               pt.medical_record_no,
                               pt.real_name AS patient_name,
                               e.real_name AS doctor_name
                        FROM prescription p
                        JOIN patient pt ON p.patient_id = pt.id
                        JOIN employee e ON p.doctor_id = e.id
                        WHERE p.delmark = 0
                          AND (CAST(:status AS INTEGER) IS NULL OR p.status = CAST(:status AS INTEGER))
                        ORDER BY p.create_time
                        LIMIT :limit OFFSET :offset
                        """)
                .param("status", status)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("prescriptionId", rs.getLong("prescription_id"));
                    row.put("registerId", rs.getLong("register_id"));
                    row.put("totalAmount", rs.getBigDecimal("total_amount"));
                    row.put("status", rs.getInt("status"));
                    row.put("createTime", rs.getObject("create_time", OffsetDateTime.class));
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("patientName", rs.getString("patient_name"));
                    row.put("doctorName", rs.getString("doctor_name"));
                    return row;
                })
                .list();
        for (Map<String, Object> prescription : list) {
            Long prescriptionId = ((Number) prescription.get("prescriptionId")).longValue();
            prescription.put("items", findItemsByPrescriptionId(prescriptionId));
        }
        return list;
    }

    public List<Map<String, Object>> findByRegisterId(Long registerId) {
        List<Map<String, Object>> prescriptions = jdbcClient.sql("""
                        SELECT p.id AS prescription_id,
                               p.register_id,
                               p.patient_id,
                               p.doctor_id,
                               p.total_amount,
                               p.status,
                               p.reject_reason,
                               p.create_time,
                               pt.medical_record_no,
                               pt.real_name AS patient_name,
                               e.real_name AS doctor_name
                        FROM prescription p
                        JOIN patient pt ON p.patient_id = pt.id
                        JOIN employee e ON p.doctor_id = e.id
                        WHERE p.register_id = :registerId AND p.delmark = 0
                        ORDER BY p.create_time ASC
                        """)
                .param("registerId", registerId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("prescriptionId", rs.getLong("prescription_id"));
                    row.put("registerId", rs.getLong("register_id"));
                    row.put("patientId", rs.getLong("patient_id"));
                    row.put("doctorId", rs.getLong("doctor_id"));
                    row.put("totalAmount", rs.getBigDecimal("total_amount"));
                    row.put("status", rs.getInt("status"));
                    row.put("rejectReason", rs.getString("reject_reason"));
                    row.put("createTime", rs.getObject("create_time", OffsetDateTime.class));
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("patientName", rs.getString("patient_name"));
                    row.put("doctorName", rs.getString("doctor_name"));
                    return row;
                })
                .list();
        for (Map<String, Object> prescription : prescriptions) {
            Long prescriptionId = ((Number) prescription.get("prescriptionId")).longValue();
            prescription.put("items", findItemsByPrescriptionId(prescriptionId));
        }
        return prescriptions;
    }

    public List<Map<String, Object>> findItemsByPrescriptionId(Long prescriptionId) {
        return jdbcClient.sql("""
                        SELECT drug_id, drug_code, drug_name, drug_format, drug_dosage, drug_type,
                               quantity, unit_price, amount, usage_method, dosage, frequency, days,
                               entrust, sort_no
                        FROM prescription_item
                        WHERE prescription_id = :prescriptionId
                        ORDER BY sort_no, id
                        """)
                .param("prescriptionId", prescriptionId)
                .query((rs, rowNum) -> mapPrescriptionItemRow(rs))
                .list();
    }

    public Optional<Map<String, Object>> findDetailById(Long id) {
        return jdbcClient.sql("""
                        SELECT p.id AS prescription_id,
                               p.register_id,
                               p.patient_id,
                               p.doctor_id,
                               p.total_amount,
                               p.status,
                               p.create_time,
                               p.reject_reason,
                               p.reject_time,
                               pt.medical_record_no,
                               pt.real_name AS patient_name,
                               e.real_name AS doctor_name,
                               rejecter.real_name AS reject_pharmacist_name
                        FROM prescription p
                        JOIN patient pt ON p.patient_id = pt.id
                        JOIN employee e ON p.doctor_id = e.id
                        LEFT JOIN employee rejecter ON p.reject_pharmacist_id = rejecter.id
                        WHERE p.id = :id AND p.delmark = 0
                        """)
                .param("id", id)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("prescriptionId", rs.getLong("prescription_id"));
                    row.put("registerId", rs.getLong("register_id"));
                    row.put("patientId", rs.getLong("patient_id"));
                    row.put("doctorId", rs.getLong("doctor_id"));
                    row.put("totalAmount", rs.getBigDecimal("total_amount"));
                    row.put("status", rs.getInt("status"));
                    row.put("createTime", rs.getObject("create_time", OffsetDateTime.class));
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("patientName", rs.getString("patient_name"));
                    row.put("doctorName", rs.getString("doctor_name"));
                    row.put("rejectReason", rs.getString("reject_reason"));
                    row.put("rejectTime", rs.getObject("reject_time", OffsetDateTime.class));
                    row.put("rejectPharmacistName", rs.getString("reject_pharmacist_name"));
                    return row;
                })
                .optional();
    }

    public List<Map<String, Object>> findItemsWithStockByPrescriptionId(Long prescriptionId) {
        return jdbcClient.sql("""
                        SELECT pi.drug_id, pi.drug_code, pi.drug_name, pi.drug_format, pi.drug_dosage, pi.drug_type,
                               pi.quantity, pi.unit_price, pi.amount, pi.usage_method, pi.dosage, pi.frequency,
                               pi.days, pi.entrust, pi.sort_no,
                               d.stock_qty
                        FROM prescription_item pi
                        JOIN drug_info d ON pi.drug_id = d.id
                        WHERE pi.prescription_id = :prescriptionId
                        ORDER BY pi.sort_no, pi.id
                        """)
                .param("prescriptionId", prescriptionId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = mapPrescriptionItemRow(rs);
                    row.put("stockQty", rs.getBigDecimal("stock_qty"));
                    return row;
                })
                .list();
    }

    /** Sets status to 15 (PHARMACY_REJECTED); only from status 20 (PAID). */
    public int markPharmacyRejected(Long id, Long pharmacistId, String reason) {
        return jdbcClient.sql("""
                        UPDATE prescription
                        SET status = 15,
                            reject_reason = :reason,
                            reject_pharmacist_id = :pharmacistId,
                            reject_time = NOW(),
                            update_time = NOW()
                        WHERE id = :id AND status = 20 AND delmark = 0
                        """)
                .param("id", id)
                .param("pharmacistId", pharmacistId)
                .param("reason", reason)
                .update();
    }

    /** Clears reject fields and sets status to 10 (ORDERED); only from status 15 (PHARMACY_REJECTED). */
    public int clearRejectFieldsAndSetOrdered(Long id, BigDecimal totalAmount) {
        return jdbcClient.sql("""
                        UPDATE prescription
                        SET status = 10,
                            reject_reason = NULL,
                            reject_pharmacist_id = NULL,
                            reject_time = NULL,
                            total_amount = :totalAmount,
                            update_time = NOW()
                        WHERE id = :id AND status = 15 AND delmark = 0
                        """)
                .param("id", id)
                .param("totalAmount", totalAmount)
                .update();
    }

    public void deleteItemsByPrescriptionId(Long prescriptionId) {
        jdbcClient.sql("""
                        DELETE FROM prescription_item
                        WHERE prescription_id = :prescriptionId
                        """)
                .param("prescriptionId", prescriptionId)
                .update();
    }

    public int updateTotalAmount(Long id, BigDecimal totalAmount) {
        return jdbcClient.sql("""
                        UPDATE prescription
                        SET total_amount = :totalAmount, update_time = NOW()
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", id)
                .param("totalAmount", totalAmount)
                .update();
    }

    private Map<String, Object> mapPrescriptionItemRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("drugId", rs.getLong("drug_id"));
        row.put("drugCode", rs.getString("drug_code"));
        row.put("drugName", rs.getString("drug_name"));
        row.put("drugFormat", rs.getString("drug_format"));
        row.put("drugDosage", rs.getString("drug_dosage"));
        row.put("drugType", rs.getString("drug_type"));
        row.put("quantity", rs.getBigDecimal("quantity"));
        row.put("unitPrice", rs.getBigDecimal("unit_price"));
        row.put("amount", rs.getBigDecimal("amount"));
        row.put("usageMethod", rs.getString("usage_method"));
        row.put("dosage", rs.getString("dosage"));
        row.put("frequency", rs.getString("frequency"));
        row.put("days", rs.getObject("days", Integer.class));
        row.put("entrust", rs.getString("entrust"));
        row.put("sortNo", rs.getInt("sort_no"));
        return row;
    }

    public void markDispensed(Long prescriptionId, Long pharmacistId) {
        jdbcClient.sql("""
                        UPDATE prescription
                        SET status = 30, pharmacist_id = :pharmacistId, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", prescriptionId)
                .param("pharmacistId", pharmacistId)
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
