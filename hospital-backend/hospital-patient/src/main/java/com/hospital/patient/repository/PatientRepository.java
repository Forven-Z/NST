package com.hospital.patient.repository;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.dto.patient.PatientProfileResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PatientRepository {

    private final JdbcClient jdbcClient;

    public PatientRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<PatientProfileResponse> findProfileById(Long patientId) {
        return jdbcClient.sql("""
                        SELECT p.id, p.medical_record_no, p.real_name, p.gender, p.birth_date,
                               p.phone, p.id_card, p.address, p.settle_category_id,
                               sc.category_name AS settle_category_name
                        FROM patient p
                        LEFT JOIN settle_category sc ON p.settle_category_id = sc.id
                        WHERE p.id = :id AND p.delmark = 0
                        """)
                .param("id", patientId)
                .query((rs, rowNum) -> PatientProfileResponse.builder()
                        .id(rs.getLong("id"))
                        .medicalRecordNo(rs.getString("medical_record_no"))
                        .realName(rs.getString("real_name"))
                        .gender(rs.getObject("gender", Integer.class))
                        .birthDate(rs.getObject("birth_date", java.time.LocalDate.class))
                        .phone(rs.getString("phone"))
                        .idCard(rs.getString("id_card"))
                        .address(rs.getString("address"))
                        .settleCategoryId(rs.getObject("settle_category_id", Long.class))
                        .settleCategoryName(rs.getString("settle_category_name"))
                        .build())
                .optional();
    }

    public Optional<Long> findPatientIdByOpenid(String openid) {
        return jdbcClient.sql("""
                        SELECT patient_id FROM patient_wechat WHERE openid = :openid
                        """)
                .param("openid", openid)
                .query(Long.class)
                .optional();
    }

    public long insertPatient(String medicalRecordNo, String realName) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO patient (medical_record_no, real_name, gender, need_medical_book)
                        VALUES (:medicalRecordNo, :realName, 0, FALSE)
                        """)
                .param("medicalRecordNo", medicalRecordNo)
                .param("realName", realName)
                .update(keyHolder, "id");
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    public void upsertWechatBinding(Long patientId, String openid) {
        jdbcClient.sql("""
                        INSERT INTO patient_wechat (patient_id, openid, last_login_time)
                        VALUES (:patientId, :openid, :now)
                        ON CONFLICT (openid) DO UPDATE
                        SET last_login_time = EXCLUDED.last_login_time,
                            update_time = NOW()
                        """)
                .param("patientId", patientId)
                .param("openid", openid)
                .param("now", OffsetDateTime.now())
                .update();
    }

    public void updateProfile(Long patientId, String realName, Integer gender, java.time.LocalDate birthDate,
                              Integer age, String phone, String idCard, String address, Long settleCategoryId) {
        jdbcClient.sql("""
                        UPDATE patient AS p
                        SET real_name = COALESCE(:realName, p.real_name),
                            gender = COALESCE(:gender, p.gender),
                            birth_date = COALESCE(:birthDate, p.birth_date),
                            age = COALESCE(
                                :age,
                                CASE WHEN COALESCE(:birthDate, p.birth_date) IS NOT NULL
                                     THEN EXTRACT(YEAR FROM AGE(CURRENT_DATE, COALESCE(:birthDate, p.birth_date)))::INTEGER
                                     ELSE p.age END),
                            phone = COALESCE(:phone, p.phone),
                            id_card = COALESCE(:idCard, p.id_card),
                            address = COALESCE(:address, p.address),
                            settle_category_id = COALESCE(:settleCategoryId, p.settle_category_id),
                            update_time = NOW()
                        WHERE p.id = :id AND p.delmark = 0
                        """)
                .param("id", patientId)
                .param("realName", realName)
                .param("gender", gender)
                .param("birthDate", birthDate)
                .param("age", age)
                .param("phone", phone)
                .param("idCard", idCard)
                .param("address", address)
                .param("settleCategoryId", settleCategoryId)
                .update();
    }

    public String findMedicalRecordNo(Long patientId) {
        return jdbcClient.sql("SELECT medical_record_no FROM patient WHERE id = :id")
                .param("id", patientId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    public Optional<Long> findPatientIdByMedicalRecordNo(String medicalRecordNo) {
        if (medicalRecordNo == null || medicalRecordNo.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient.sql("""
                        SELECT id FROM patient WHERE medical_record_no = :mrn AND delmark = 0
                        """)
                .param("mrn", medicalRecordNo.trim())
                .query(Long.class)
                .optional();
    }

    public Optional<Long> findPatientIdByIdCard(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient.sql("""
                        SELECT id FROM patient WHERE id_card = :idCard AND delmark = 0
                        """)
                .param("idCard", idCard.trim().toUpperCase())
                .query(Long.class)
                .optional();
    }

    /** 姓名精确匹配（非模糊） */
    public List<Map<String, Object>> listPatientSummariesByRealName(String realName, int limit) {
        if (realName == null || realName.isBlank()) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return jdbcClient.sql("""
                        SELECT id, medical_record_no, real_name, gender, birth_date, id_card
                        FROM patient
                        WHERE real_name = :realName AND delmark = 0
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .param("realName", realName.trim())
                .param("limit", safeLimit)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("patientId", rs.getLong("id"));
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("realName", rs.getString("real_name"));
                    row.put("gender", rs.getObject("gender", Integer.class));
                    row.put("birthDate", rs.getObject("birth_date", java.time.LocalDate.class));
                    row.put("idCard", rs.getString("id_card"));
                    return row;
                })
                .list();
    }

    public Optional<Long> findPatientIdByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient.sql("""
                        SELECT id FROM patient WHERE phone = :phone AND delmark = 0
                        """)
                .param("phone", phone.trim())
                .query(Long.class)
                .optional();
    }

    /**
     * 手机号可选（儿童等留空）；非空时校验格式且全局唯一（与 ux_patient_phone 一致）。
     */
    public void assertPhoneAvailable(String phone, Long excludePatientId) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        String normalized = phone.trim();
        if (!normalized.matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式不正确");
        }
        findPatientIdByPhone(normalized).ifPresent(existingId -> {
            if (excludePatientId == null || !existingId.equals(excludePatientId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该手机号已被其他就诊人使用");
            }
        });
    }

    public boolean hasWechatBinding(Long patientId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM patient_wechat WHERE patient_id = :patientId
                        """)
                .param("patientId", patientId)
                .query(Integer.class)
                .single() > 0;
    }

    public Optional<String> findOpenidByPatientId(Long patientId) {
        return jdbcClient.sql("""
                        SELECT openid FROM patient_wechat WHERE patient_id = :patientId
                        """)
                .param("patientId", patientId)
                .query(String.class)
                .optional();
    }

    public void rebindWechatPatient(String openid, Long newPatientId) {
        jdbcClient.sql("""
                        UPDATE patient_wechat
                        SET patient_id = :newPatientId, update_time = NOW()
                        WHERE openid = :openid
                        """)
                .param("openid", openid)
                .param("newPatientId", newPatientId)
                .update();
    }

    public void softDeletePatient(Long patientId) {
        jdbcClient.sql("""
                        UPDATE patient SET delmark = 1, update_time = NOW() WHERE id = :id
                        """)
                .param("id", patientId)
                .update();
    }

    public void updateNeedMedicalBook(Long patientId, boolean need) {
        jdbcClient.sql("""
                        UPDATE patient SET need_medical_book = :need, update_time = NOW()
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", patientId)
                .param("need", need)
                .update();
    }

    public long insertFamilyPatient(String medicalRecordNo, String realName, Integer gender,
                                    java.time.LocalDate birthDate, Integer age, String idCard, String phone,
                                    String address) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO patient (medical_record_no, real_name, gender, birth_date, age, id_card, phone, address, need_medical_book)
                        VALUES (:medicalRecordNo, :realName, :gender, :birthDate,
                                COALESCE(:age, CASE WHEN :birthDate IS NOT NULL
                                    THEN EXTRACT(YEAR FROM AGE(CURRENT_DATE, :birthDate))::INTEGER END),
                                :idCard, :phone, :address, FALSE)
                        """)
                .param("medicalRecordNo", medicalRecordNo)
                .param("realName", realName)
                .param("gender", gender != null ? gender : 0)
                .param("birthDate", birthDate)
                .param("age", age)
                .param("idCard", idCard)
                .param("phone", phone)
                .param("address", address)
                .update(keyHolder, "id");
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }
}
