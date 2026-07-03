package com.hospital.patient.clinicalsync;

import com.hospital.common.constant.ClinicalSyncTaskStatus;
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
public class ClinicalSyncTaskRepository {

    private final JdbcClient jdbcClient;

    public ClinicalSyncTaskRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long enqueue(String bizType, long bizId, String action, int maxRetries) {
        Optional<Map<String, Object>> existing = findByKey(bizType, bizId, action);
        if (existing.isPresent()) {
            Map<String, Object> row = existing.get();
            long id = ((Number) row.get("id")).longValue();
            resetToPending(id);
            return id;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO clinical_sync_task (biz_type, biz_id, action, status, max_retries, next_retry_at)
                        VALUES (:bizType, :bizId, :action, :status, :maxRetries, NOW())
                        """)
                .param("bizType", bizType)
                .param("bizId", bizId)
                .param("action", action)
                .param("status", ClinicalSyncTaskStatus.PENDING)
                .param("maxRetries", maxRetries)
                .update(keyHolder, "id");
        return keyHolder.getKey().longValue();
    }

    public Optional<Map<String, Object>> findByIdForUpdate(long taskId) {
        return jdbcClient.sql("""
                        SELECT id, biz_type, biz_id, action, status, retry_count, max_retries,
                               next_retry_at, last_error
                        FROM clinical_sync_task
                        WHERE id = :id
                        FOR UPDATE
                        """)
                .param("id", taskId)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    public List<Long> findDueTaskIds(int limit) {
        return jdbcClient.sql("""
                        SELECT id
                        FROM clinical_sync_task
                        WHERE status IN (:pending, :failed)
                          AND (next_retry_at IS NULL OR next_retry_at <= NOW())
                        ORDER BY id
                        LIMIT :limit
                        """)
                .param("pending", ClinicalSyncTaskStatus.PENDING)
                .param("failed", ClinicalSyncTaskStatus.FAILED)
                .param("limit", limit)
                .query((rs, rowNum) -> rs.getLong("id"))
                .list();
    }

    public void markDone(long taskId) {
        jdbcClient.sql("""
                        UPDATE clinical_sync_task
                        SET status = :status, last_error = NULL, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", taskId)
                .param("status", ClinicalSyncTaskStatus.DONE)
                .update();
    }

    public void markFailed(long taskId, int retryCount, String error, OffsetDateTime nextRetryAt) {
        jdbcClient.sql("""
                        UPDATE clinical_sync_task
                        SET status = :status,
                            retry_count = :retryCount,
                            last_error = :error,
                            next_retry_at = :nextRetryAt,
                            update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", taskId)
                .param("status", ClinicalSyncTaskStatus.FAILED)
                .param("retryCount", retryCount)
                .param("error", truncate(error, 2000))
                .param("nextRetryAt", nextRetryAt)
                .update();
    }

    public void markDead(long taskId, int retryCount, String error) {
        jdbcClient.sql("""
                        UPDATE clinical_sync_task
                        SET status = :status,
                            retry_count = :retryCount,
                            last_error = :error,
                            update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", taskId)
                .param("status", ClinicalSyncTaskStatus.DEAD)
                .param("retryCount", retryCount)
                .param("error", truncate(error, 2000))
                .update();
    }

    private void resetToPending(long taskId) {
        jdbcClient.sql("""
                        UPDATE clinical_sync_task
                        SET status = :status, next_retry_at = NOW(), update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", taskId)
                .param("status", ClinicalSyncTaskStatus.PENDING)
                .update();
    }

    private Optional<Map<String, Object>> findByKey(String bizType, long bizId, String action) {
        return jdbcClient.sql("""
                        SELECT id, biz_type, biz_id, action, status, retry_count, max_retries,
                               next_retry_at, last_error
                        FROM clinical_sync_task
                        WHERE biz_type = :bizType AND biz_id = :bizId AND action = :action
                        """)
                .param("bizType", bizType)
                .param("bizId", bizId)
                .param("action", action)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    private Map<String, Object> mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("bizType", rs.getString("biz_type"));
        row.put("bizId", rs.getLong("biz_id"));
        row.put("action", rs.getString("action"));
        row.put("status", rs.getString("status"));
        row.put("retryCount", rs.getInt("retry_count"));
        row.put("maxRetries", rs.getInt("max_retries"));
        row.put("nextRetryAt", rs.getObject("next_retry_at", OffsetDateTime.class));
        row.put("lastError", rs.getString("last_error"));
        return row;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
