package com.hospital.patient.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 患者域只读：处方查询（开立/发药/驳回在 hospital-his / hospital-pharmacy）。 */
@Repository
public class PrescriptionRepository {

    private final JdbcClient jdbcClient;

    public PrescriptionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
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
}
