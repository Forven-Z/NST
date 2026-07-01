package com.hospital.patient.clinicalsync;

import com.hospital.common.constant.ClinicalSyncAction;
import com.hospital.common.constant.ClinicalSyncTaskStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.client.ClinicalOrderBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicalSyncService {

    private static final int DEFAULT_MAX_RETRIES = 10;
    private static final int RETRY_BATCH_SIZE = 50;

    private final ClinicalSyncTaskRepository clinicalSyncTaskRepository;
    private final ClinicalOrderBridge clinicalOrderBridge;
    private final TransactionTemplate transactionTemplate;

    public long enqueueOnBillPaid(String bizType, long bizId) {
        return enqueue(bizType, bizId, ClinicalSyncAction.ON_BILL_PAID);
    }

    public long enqueueOnRefund(String bizType, long bizId) {
        return enqueue(bizType, bizId, ClinicalSyncAction.ON_REFUND);
    }

    public long enqueue(String bizType, long bizId, String action) {
        if (!clinicalOrderBridge.handles(bizType)) {
            return -1L;
        }
        return clinicalSyncTaskRepository.enqueue(bizType, bizId, action, DEFAULT_MAX_RETRIES);
    }

    public void scheduleProcessAfterCommit(List<Long> taskIds) {
        List<Long> ids = taskIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Runnable runner = () -> processTaskIds(ids);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runner.run();
                }
            });
        } else {
            runner.run();
        }
    }

    public void processTaskIds(List<Long> taskIds) {
        for (Long taskId : taskIds) {
            try {
                processOne(taskId);
            } catch (Exception ex) {
                log.error("clinical 同步任务处理异常 taskId={}", taskId, ex);
            }
        }
    }

    public void processOne(long taskId) {
        transactionTemplate.executeWithoutResult(status -> doProcessOne(taskId));
    }

    private void doProcessOne(long taskId) {
        Map<String, Object> task = clinicalSyncTaskRepository.findByIdForUpdate(taskId)
                .orElse(null);
        if (task == null) {
            return;
        }
        String status = (String) task.get("status");
        if (ClinicalSyncTaskStatus.DONE.equals(status) || ClinicalSyncTaskStatus.DEAD.equals(status)) {
            return;
        }

        String bizType = (String) task.get("bizType");
        long bizId = ((Number) task.get("bizId")).longValue();
        String action = (String) task.get("action");
        int retryCount = ((Number) task.get("retryCount")).intValue();
        int maxRetries = ((Number) task.get("maxRetries")).intValue();

        try {
            dispatch(action, bizType, bizId);
            clinicalSyncTaskRepository.markDone(taskId);
            log.info("clinical 同步成功 taskId={} action={} bizType={} bizId={}", taskId, action, bizType, bizId);
        } catch (BusinessException ex) {
            handleFailure(taskId, retryCount, maxRetries, ex.getMessage());
        } catch (Exception ex) {
            handleFailure(taskId, retryCount, maxRetries, ex.getMessage());
        }
    }

    public void retryDueTasks() {
        List<Long> dueIds = clinicalSyncTaskRepository.findDueTaskIds(RETRY_BATCH_SIZE);
        if (dueIds.isEmpty()) {
            return;
        }
        log.debug("clinical 同步重试 batchSize={}", dueIds.size());
        processTaskIds(dueIds);
    }

    private void dispatch(String action, String bizType, long bizId) {
        if (ClinicalSyncAction.ON_BILL_PAID.equals(action)) {
            clinicalOrderBridge.onBillPaid(bizType, bizId);
            return;
        }
        if (ClinicalSyncAction.ON_REFUND.equals(action)) {
            clinicalOrderBridge.onRefund(bizType, bizId);
            return;
        }
        throw new IllegalStateException("未知同步动作: " + action);
    }

    private void handleFailure(long taskId, int retryCount, int maxRetries, String error) {
        int nextRetry = retryCount + 1;
        String message = error != null ? error : "clinical 同步失败";
        if (nextRetry >= maxRetries) {
            clinicalSyncTaskRepository.markDead(taskId, nextRetry, message);
            log.error("clinical 同步进入 DEAD taskId={} retries={} error={}", taskId, nextRetry, message);
            return;
        }
        OffsetDateTime nextRetryAt = OffsetDateTime.now().plusSeconds(backoffSeconds(nextRetry));
        clinicalSyncTaskRepository.markFailed(taskId, nextRetry, message, nextRetryAt);
        log.warn("clinical 同步失败将重试 taskId={} retry={} nextRetryAt={} error={}",
                taskId, nextRetry, nextRetryAt, message);
    }

    private static long backoffSeconds(int retryCount) {
        return Math.min(300L, (long) Math.pow(2, Math.min(retryCount, 8)));
    }

    public List<Long> newTaskIdList() {
        return new ArrayList<>();
    }
}
