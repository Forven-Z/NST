package com.hospital.his.repository;

import com.hospital.his.dto.patient.PatientProfileResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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
                              String phone, String idCard, String address, Long settleCategoryId) {
        jdbcClient.sql("""
                        UPDATE patient
                        SET real_name = COALESCE(:realName, real_name),
                            gender = COALESCE(:gender, gender),
                            birth_date = COALESCE(:birthDate, birth_date),
                            phone = COALESCE(:phone, phone),
                            id_card = COALESCE(:idCard, id_card),
                            address = COALESCE(:address, address),
                            settle_category_id = COALESCE(:settleCategoryId, settle_category_id),
                            update_time = NOW()
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", patientId)
                .param("realName", realName)
                .param("gender", gender)
                .param("birthDate", birthDate)
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
        return jdbcClient.sql("""
                        SELECT id FROM patient WHERE medical_record_no = :mrn AND delmark = 0
                        """)
                .param("mrn", medicalRecordNo)
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
                .param("idCard", idCard)
                .query(Long.class)
                .optional();
    }

    public long insertFamilyPatient(String medicalRecordNo, String realName, Integer gender,
                                    String idCard, String phone) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO patient (medical_record_no, real_name, gender, id_card, phone, need_medical_book)
                        VALUES (:medicalRecordNo, :realName, :gender, :idCard, :phone, FALSE)
                        """)
                .param("medicalRecordNo", medicalRecordNo)
                .param("realName", realName)
                .param("gender", gender != null ? gender : 0)
                .param("idCard", idCard)
                .param("phone", phone)
                .update(keyHolder, "id");
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }
}
