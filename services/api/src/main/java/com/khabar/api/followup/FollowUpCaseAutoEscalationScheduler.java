package com.khabar.api.followup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Opt-in queue routing; it deliberately does not send or imply a staff notification. */
@Component
@ConditionalOnProperty(name = "khabar.follow-up.auto-escalation.enabled", havingValue = "true")
public class FollowUpCaseAutoEscalationScheduler {

    private final FollowUpCases cases;

    public FollowUpCaseAutoEscalationScheduler(FollowUpCases cases) {
        this.cases = cases;
    }

    @Scheduled(fixedDelayString = "${khabar.follow-up.auto-escalation.poll-ms:60000}",
            initialDelayString = "${khabar.follow-up.auto-escalation.poll-ms:60000}")
    public void routeOverdueCases() {
        cases.autoEscalateOverdueCases();
    }
}
