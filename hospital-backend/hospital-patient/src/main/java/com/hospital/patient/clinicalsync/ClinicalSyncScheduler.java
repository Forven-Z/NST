package com.hospital.patient.clinicalsync;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClinicalSyncScheduler {

    private final ClinicalSyncService clinicalSyncService;

    @Scheduled(fixedDelayString = "${hospital.clinical-sync.retry-interval-ms:30000}")
    public void retryDueTasks() {
        clinicalSyncService.retryDueTasks();
    }
}
