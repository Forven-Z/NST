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
                               p.id AS member_patient_id,
                               p.medical_record_no,
                               p.real_name,
                               p.gender,
                               p.id_card,
                               p.phone
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
                    row.put("memberPatientId", rs.getLong("member_patient_id"));
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("realName", rs.getString("real_name"));
                    row.put("gender", rs.getObject("gender", Integer.class));
                    row.put("idCard", rs.getString("id_card"));
                    row.put("phone", rs.getString("phone"));
                    return row;
                })
                .list();
    }

    public long insertLink(Long ownerPatientId, Long memberPatientId, int relationType) {
        Optional<Long> existing = findLinkIdAny(ownerPatientId, memberPatientId);
        if (existing.isPresent()) {
            jdbcClient.sql("""
                            UPDATE patient_family_link
                            SET relation_type = :relationType, delmark = 0, update_time = NOW()
                            WHERE id = :id
                            """)
                    .param("id", existing.get())
                    .param("relationType", relationType)
                    .update();
            return existing.get();
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO patient_family_link (owner_patient_id, member_patient_id, relation_type)
                        VALUES (:ownerId, :memberId, :relationType)
                        """)
                .param("ownerId", ownerPatientId)
                .param("memberId", memberPatientId)
                .param("relationType", relationType)
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
}
