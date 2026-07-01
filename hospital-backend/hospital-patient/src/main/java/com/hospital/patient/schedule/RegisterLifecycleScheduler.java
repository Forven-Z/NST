package com.hospital.patient.schedule;

import com.hospital.patient.service.RegisterLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterLifecycleScheduler {

    private final RegisterLifecycleService registerLifecycleService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.debug("Register lifecycle startup maintenance");
        registerLifecycleService.runMaintenanceOnStartup();
    }

    /** 待支付超时：每 2 分钟扫描一次。 */
    @Scheduled(fixedDelay = 120_000, initialDelay = 30_000)
    public void expirePendingPayments() {
        registerLifecycleService.expirePendingPayments();
    }

    /** 日终关单：21:00 后每 15 分钟扫描（含历史漏关补偿）。 */
    @Scheduled(fixedDelay = 900_000, initialDelay = 60_000)
    public void autoDayCloseRegisters() {
        registerLifecycleService.autoDayCloseRegisters();
    }
}
