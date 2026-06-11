package com.hospital.pacs.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ImagingStudyRepository {

    private final JdbcClient jdbcClient;

    public ImagingStudyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Map<String, Object>> findByCheckRequestId(Long checkRequestId) {
        return jdbcClient.sql("""
                        SELECT id, study_no, check_request_id, register_id, patient_id, modality, status,
                               source_bucket, source_object_key, result_bucket, result_object_key,
                               report_json::text AS report_json, error_message, complete_time
                        FROM imaging_study
                        WHERE check_request_id = :checkRequestId
                        ORDER BY id DESC
                        LIMIT 1
                        """)
                .param("checkRequestId", checkRequestId)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    public Optional<Map<String, Object>> findById(Long studyId) {
        return jdbcClient.sql("""
                        SELECT id, study_no, check_request_id, register_id, patient_id, modality, status,
                               source_bucket, source_object_key, result_bucket, result_object_key,
                               report_json::text AS report_json, error_message, complete_time
                        FROM imaging_study
                        WHERE id = :id
                        """)
                .param("id", studyId)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    public long insertPending(Long checkRequestId, Long registerId, Long patientId, String modality,
                              String sourceBucket, String sourceObjectKey) {
        String studyNo = "IMG" + checkRequestId + "-" + System.currentTimeMillis();
        return jdbcClient.sql("""
                        INSERT INTO imaging_study (
                            study_no, check_request_id, register_id, patient_id, modality, status,
                            source_bucket, source_object_key, submit_time, create_time, update_time
                        ) VALUES (
                            :studyNo, :checkRequestId, :registerId, :patientId, :modality, 'PENDING',
                            :sourceBucket, :sourceObjectKey, :now, :now, :now
                        )
                        RETURNING id
                        """)
                .param("studyNo", studyNo)
                .param("checkRequestId", checkRequestId)
                .param("registerId", registerId)
                .param("patientId", patientId)
                .param("modality", modality)
                .param("sourceBucket", sourceBucket)
                .param("sourceObjectKey", sourceObjectKey)
                .param("now", OffsetDateTime.now())
                .query(Long.class)
                .single();
    }

    public void resetToPending(Long studyId, String sourceBucket, String sourceObjectKey) {
        jdbcClient.sql("""
                        UPDATE imaging_study
                        SET status = 'PENDING',
                            source_bucket = :sourceBucket,
                            source_object_key = :sourceObjectKey,
                            result_bucket = NULL,
                            result_object_key = NULL,
                            report_json = NULL,
                            error_message = NULL,
                            complete_time = NULL,
                            update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", studyId)
                .param("sourceBucket", sourceBucket)
                .param("sourceObjectKey", sourceObjectKey)
                .update();
    }

    public void markProcessing(Long studyId) {
        jdbcClient.sql("""
                        UPDATE imaging_study
                        SET status = 'PROCESSING', update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", studyId)
                .update();
    }

    public void markCompleted(Long studyId, String resultBucket, String maskObjectKey,
                              String previewObjectKey, String reportJson) {
        jdbcClient.sql("""
                        UPDATE imaging_study
                        SET status = 'COMPLETED',
                            result_bucket = :resultBucket,
                            result_object_key = :maskObjectKey,
                            report_json = CAST(:reportJson AS jsonb),
                            complete_time = :now,
                            error_message = NULL,
                            update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", studyId)
                .param("resultBucket", resultBucket)
                .param("maskObjectKey", maskObjectKey)
                .param("reportJson", reportJson)
                .param("now", OffsetDateTime.now())
                .update();
    }

    public void markFailed(Long studyId, String errorMessage) {
        String msg = errorMessage;
        if (msg != null && msg.length() > 512) {
            msg = msg.substring(0, 509) + "...";
        }
        jdbcClient.sql("""
                        UPDATE imaging_study
                        SET status = 'FAILED', error_message = :errorMessage, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", studyId)
                .param("errorMessage", msg)
                .update();
    }

    public List<Map<String, Object>> listStudies(
            String statusFilter, Long patientId, String medicalRecordNo, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT ist.id AS study_id,
                               ist.check_request_id,
                               cr.patient_id,
                               ist.modality,
                               ist.status AS study_status,
                               ist.source_object_key,
                               ist.result_object_key,
                               p.real_name AS patient_name,
                               p.medical_record_no,
                               mt.item_name,
                               cr.status AS check_status
                        FROM imaging_study ist
                        JOIN check_request cr ON ist.check_request_id = cr.id
                        JOIN patient p ON cr.patient_id = p.id
                        JOIN medical_technology mt ON cr.medical_technology_id = mt.id
                        WHERE cr.delmark = 0
                          AND (CAST(:statusFilter AS VARCHAR) IS NULL OR ist.status = CAST(:statusFilter AS VARCHAR))
                          AND (CAST(:patientId AS BIGINT) IS NULL OR cr.patient_id = :patientId)
                          AND (CAST(:medicalRecordNo AS VARCHAR) IS NULL OR p.medical_record_no = :medicalRecordNo)
                        ORDER BY ist.create_time DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("statusFilter", statusFilter)
                .param("patientId", patientId)
                .param("medicalRecordNo", medicalRecordNo)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("studyId", rs.getLong("study_id"));
                    row.put("checkRequestId", rs.getLong("check_request_id"));
                    row.put("patientId", rs.getLong("patient_id"));
                    row.put("patientName", rs.getString("patient_name"));
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("itemName", rs.getString("item_name"));
                    row.put("modality", rs.getString("modality"));
                    String studyStatus = rs.getString("study_status");
                    int checkStatus = rs.getInt("check_status");
                    row.put("status", mapListStatus(studyStatus, checkStatus));
                    row.put("uploadStatus", rs.getString("source_object_key") == null ? "WAITING" : "UPLOADED");
                    row.put("resultReady", "COMPLETED".equals(studyStatus) || checkStatus >= 40);
                    return row;
                })
                .list();
    }

    private String mapListStatus(String studyStatus, int checkStatus) {
        if ("COMPLETED".equals(studyStatus) || checkStatus >= 40) {
            return "COMPLETED";
        }
        if ("PROCESSING".equals(studyStatus) || checkStatus >= 30) {
            return "IN_PROGRESS";
        }
        return "PENDING";
    }

    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("studyNo", rs.getString("study_no"));
        row.put("checkRequestId", rs.getLong("check_request_id"));
        row.put("registerId", rs.getLong("register_id"));
        row.put("patientId", rs.getLong("patient_id"));
        row.put("modality", rs.getString("modality"));
        row.put("status", rs.getString("status"));
        row.put("sourceBucket", rs.getString("source_bucket"));
        row.put("sourceObjectKey", rs.getString("source_object_key"));
        row.put("resultBucket", rs.getString("result_bucket"));
        row.put("resultObjectKey", rs.getString("result_object_key"));
        row.put("reportJson", rs.getString("report_json"));
        row.put("errorMessage", rs.getString("error_message"));
        row.put("completeTime", rs.getObject("complete_time", OffsetDateTime.class));
        return row;
    }
}
