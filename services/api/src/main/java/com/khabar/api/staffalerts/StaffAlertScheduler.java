package com.khabar.api.staffalerts;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Checks each clinic that switched staff alerts on. The pilot profile leaves this off until a clinic's
 * alert policy has been approved; each clinic must also switch its own alerts on.
 */
@Component
@ConditionalOnProperty(name = "khabar.staff-alerts.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class StaffAlertScheduler {

    private final StaffAlerts alerts;

    public StaffAlertScheduler(StaffAlerts alerts) {
        this.alerts = alerts;
    }

    @Scheduled(fixedDelayString = "${khabar.staff-alerts.poll-ms:120000}", initialDelayString = "${khabar.staff-alerts.poll-ms:120000}")
    public void check() {
        alerts.checkAll();
    }
}
