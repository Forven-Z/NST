package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PatientFamilyRepository {

    private final JdbcClient jdbcClient;

    public PatientFamilyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> listByOwner(Long ownerPatientId) {
        return jdbcClient.sql("""
                        SELECT l.id AS link_id,
                               l.relation_type,
                               l.no_id_card,
                               l.guardian_name,
                               l.guardian_id_card,
                               l.guardian_phone,
                               p.id AS member_patient_id,
                               p.medical_record_no,
                               p.real_name,
                               p.gender,
                               p.birth_date,
                               p.id_card,
                               p.phone,
                               p.address
                        FROM patient_family_link l
                        JOIN patient p ON l.member_patient_id = p.id
                        WHERE l.owner_patient_id = :ownerId
                          AND l.delmark = 0
                          AND p.delmark = 0
                        ORDER BY l.relation_type, l.id
                        """)
                .param("ownerId", ownerPatientId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("linkId", rs.getLong("link_id"));
                    row.put("relationType", rs.getInt("relation_type"));
                    row.put("noIdCard", rs.getBoolean("no_id_card"));
                    row.put("guardianName", rs.getString("guardian_name"));
                    row.put("guardianIdCard", rs.getString("guardian_id_card"));
                    row.put("guardianPhone", rs.getString("guardian_phone"));
                    row.put("memberPatientId", rs.getLong("member_patient_id"));
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("realName", rs.getString("real_name"));
                    row.put("gender", rs.getObject("gender", Integer.class));
                    var birthDate = rs.getObject("birth_date", java.time.LocalDate.class);
                    row.put("birthDate", birthDate != null ? birthDate.toString() : null);
                    row.put("idCard", rs.getString("id_card"));
                    row.put("phone", rs.getString("phone"));
                    row.put("address", rs.getString("address"));
                    return row;
                })
                .list();
    }

    public long insertLink(Long ownerPatientId, Long memberPatientId, int relationType,
                           boolean noIdCard, String guardianName, String guardianIdCard, String guardianPhone) {
        Optional<Long> existing = findLinkIdAny(ownerPatientId, memberPatientId);
        if (existing.isPresent()) {
            jdbcClient.sql("""
                            UPDATE patient_family_link
                            SET relation_type = :relationType,
                                no_id_card = :noIdCard,
                                guardian_name = :guardianName,
                                guardian_id_card = :guardianIdCard,
                                guardian_phone = :guardianPhone,
                                delmark = 0,
                                update_time = NOW()
                            WHERE id = :id
                            """)
                    .param("id", existing.get())
                    .param("relationType", relationType)
                    .param("noIdCard", noIdCard)
                    .param("guardianName", guardianName)
                    .param("guardianIdCard", guardianIdCard)
                    .param("guardianPhone", guardianPhone)
                    .update();
            return existing.get();
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO patient_family_link
                            (owner_patient_id, member_patient_id, relation_type,
                             no_id_card, guardian_name, guardian_id_card, guardian_phone)
                        VALUES (:ownerId, :memberId, :relationType,
                                :noIdCard, :guardianName, :guardianIdCard, :guardianPhone)
                        """)
                .param("ownerId", ownerPatientId)
                .param("memberId", memberPatientId)
                .param("relationType", relationType)
                .param("noIdCard", noIdCard)
                .param("guardianName", guardianName)
                .param("guardianIdCard", guardianIdCard)
                .param("guardianPhone", guardianPhone)
                .update(keyHolder, "id");
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    public Optional<Long> findLinkId(Long ownerPatientId, Long memberPatientId) {
        return jdbcClient.sql("""
                        SELECT id FROM patient_family_link
                        WHERE owner_patient_id = :ownerId AND member_patient_id = :memberId AND delmark = 0
                        """)
                .param("ownerId", ownerPatientId)
                .param("memberId", memberPatientId)
                .query(Long.class)
                .optional();
    }

    /** 含已解绑行，用于重新绑定（唯一约束下 UPDATE 复活） */
    public Optional<Long> findLinkIdAny(Long ownerPatientId, Long memberPatientId) {
        return jdbcClient.sql("""
                        SELECT id FROM patient_family_link
                        WHERE owner_patient_id = :ownerId AND member_patient_id = :memberId
                        """)
                .param("ownerId", ownerPatientId)
                .param("memberId", memberPatientId)
                .query(Long.class)
                .optional();
    }

    public boolean isLinked(Long ownerPatientId, Long memberPatientId) {
        if (ownerPatientId.equals(memberPatientId)) {
            return true;
        }
        return findLinkId(ownerPatientId, memberPatientId).isPresent();
    }

    /** QQ 式切换：本人、监护人↔被监护人（双向） */
    public boolean canSwitchBetween(Long fromPatientId, Long toPatientId) {
        if (fromPatientId.equals(toPatientId)) {
            return true;
        }
        return findLinkId(fromPatientId, toPatientId).isPresent()
                || findLinkId(toPatientId, fromPatientId).isPresent();
    }
}
