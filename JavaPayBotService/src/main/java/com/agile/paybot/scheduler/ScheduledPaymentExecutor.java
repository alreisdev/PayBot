package com.agile.paybot.scheduler;

import com.agile.paybot.service.ScheduledPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledPaymentExecutor {

    private final ScheduledPaymentService scheduledPaymentService;

    /**
     * Executes pending scheduled payments every 5 minutes.
     * Checks for payments where scheduledDate <= now and status = PENDING.
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void executeScheduledPayments() {
        log.debug("Running scheduled payment executor...");
        try {
            scheduledPaymentService.executePendingScheduledPayments();
        } catch (Exception e) {
            log.error("Error executing scheduled payments: {}", e.getMessage(), e);
        }
    }
}