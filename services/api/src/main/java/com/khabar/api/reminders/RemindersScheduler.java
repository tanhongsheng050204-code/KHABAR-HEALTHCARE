package com.khabar.api.reminders;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends due reminders, finds no-shows and checks for quiet patients every few minutes. The pilot profile leaves this
 * off until the clinic's reminder wording has been approved.
 */
@Component
@ConditionalOnProperty(name = "khabar.reminders.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class RemindersScheduler {

    private final Reminders reminders;

    public RemindersScheduler(Reminders reminders) {
        this.reminders = reminders;
    }

    @Scheduled(fixedDelayString = "${khabar.reminders.poll-ms:300000}", initialDelayString = "${khabar.reminders.poll-ms:300000}")
    public void run() {
        reminders.runAll();
    }
}
