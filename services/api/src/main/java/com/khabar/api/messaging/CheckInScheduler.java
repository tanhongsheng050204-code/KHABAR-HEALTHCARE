package com.khabar.api.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Looks for due check-ins every minute (khabar.checkins.poll-ms). Off in tests. */
@Component
@ConditionalOnProperty(name = "khabar.checkins.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class CheckInScheduler {

    private final CheckInSender sender;

    public CheckInScheduler(CheckInSender sender) {
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${khabar.checkins.poll-ms:60000}", initialDelayString = "${khabar.checkins.poll-ms:60000}")
    public void run() {
        sender.sendDue();
    }
}
