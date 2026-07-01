package com.hospital.patient.clinicalsync;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.ClinicalSyncAction;
import com.hospital.common.constant.ClinicalSyncTaskStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.client.ClinicalOrderBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalSyncServiceTest {

    @Mock
    private ClinicalSyncTaskRepository clinicalSyncTaskRepository;

    @Mock
    private ClinicalOrderBridge clinicalOrderBridge;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private ClinicalSyncService clinicalSyncService;

    @BeforeEach
    void setUpTransactionTemplate() {
        lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void enqueueOnBillPaidCreatesTaskForClinicalBizType() {
        when(clinicalOrderBridge.handles(BillBizType.INSPECTION)).thenReturn(true);
        when(clinicalSyncTaskRepository.enqueue(
                BillBizType.INSPECTION, 100L, ClinicalSyncAction.ON_BILL_PAID, 10))
                .thenReturn(1L);

        long taskId = clinicalSyncService.enqueueOnBillPaid(BillBizType.INSPECTION, 100L);

        assertThat(taskId).isEqualTo(1L);
    }

    @Test
    void enqueueSkipsNonClinicalBizType() {
        when(clinicalOrderBridge.handles(BillBizType.REGISTER)).thenReturn(false);

        long taskId = clinicalSyncService.enqueueOnBillPaid(BillBizType.REGISTER, 1L);

        assertThat(taskId).isEqualTo(-1L);
        verify(clinicalSyncTaskRepository, never()).enqueue(any(), any(Long.class), any(), any(Integer.class));
    }

    @Test
    void processOneMarksDoneWhenClinicalSyncSucceeds() {
        when(clinicalSyncTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingTask()));

        clinicalSyncService.processOne(1L);

        verify(clinicalOrderBridge).onBillPaid(BillBizType.PRESCRIPTION, 200L);
        verify(clinicalSyncTaskRepository).markDone(1L);
    }

    @Test
    void processOneMarksFailedWhenClinicalSyncFails() {
        when(clinicalSyncTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingTask()));
        doThrow(new BusinessException(400, "clinical 不可用"))
                .when(clinicalOrderBridge).onBillPaid(BillBizType.PRESCRIPTION, 200L);

        clinicalSyncService.processOne(1L);

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(clinicalSyncTaskRepository).markFailed(
                eq(1L),
                eq(1),
                errorCaptor.capture(),
                any());
        assertThat(errorCaptor.getValue()).contains("clinical 不可用");
    }

    @Test
    void processTaskIdsContinuesWhenOneTaskFails() {
        when(clinicalSyncTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pendingTask(1L)));
        when(clinicalSyncTaskRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(pendingTask(2L)));
        doThrow(new BusinessException(400, "fail-1"))
                .doNothing()
                .when(clinicalOrderBridge).onBillPaid(BillBizType.PRESCRIPTION, 200L);

        clinicalSyncService.processTaskIds(List.of(1L, 2L));

        verify(clinicalSyncTaskRepository).markFailed(eq(1L), eq(1), any(), any());
        verify(clinicalSyncTaskRepository).markDone(2L);
    }

    private static Map<String, Object> pendingTask() {
        return pendingTask(1L);
    }

    private static Map<String, Object> pendingTask(long taskId) {
        Map<String, Object> task = new HashMap<>();
        task.put("id", taskId);
        task.put("bizType", BillBizType.PRESCRIPTION);
        task.put("bizId", 200L);
        task.put("action", ClinicalSyncAction.ON_BILL_PAID);
        task.put("status", ClinicalSyncTaskStatus.PENDING);
        task.put("retryCount", 0);
        task.put("maxRetries", 10);
        return task;
    }
}
