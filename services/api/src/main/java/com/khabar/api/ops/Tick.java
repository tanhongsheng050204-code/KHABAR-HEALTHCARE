package com.khabar.api.ops;

import com.khabar.api.audit.AuditChainSealer;
import com.khabar.api.followup.FollowUpCaseAutoEscalationScheduler;
import com.khabar.api.messaging.CheckInScheduler;
import com.khabar.api.reminders.RemindersScheduler;
import com.khabar.api.staffalerts.StaffAlertScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A host that sleeps between requests (Vercel) never runs the timed jobs while asleep. An outside schedule calls this
 * every few minutes with a shared secret, and it runs each job the deployment has switched on, exactly as its own
 * timer would: check-ins, reminders, staff alerts, case routing and the audit seal. Each job is safe to run twice.
 * Without a secret configured the endpoint does not exist.
 */
@RestController
public class Tick {

    private static final Logger log = LoggerFactory.getLogger(Tick.class);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final String secret;
    private final ObjectProvider<CheckInScheduler> checkIns;
    private final ObjectProvider<RemindersScheduler> reminders;
    private final ObjectProvider<StaffAlertScheduler> staffAlerts;
    private final ObjectProvider<FollowUpCaseAutoEscalationScheduler> escalation;
    private final ObjectProvider<AuditChainSealer> sealer;

    public Tick(@Value("${khabar.tick.secret:}") String secret, ObjectProvider<CheckInScheduler> checkIns,
                ObjectProvider<RemindersScheduler> reminders, ObjectProvider<StaffAlertScheduler> staffAlerts,
                ObjectProvider<FollowUpCaseAutoEscalationScheduler> escalation, ObjectProvider<AuditChainSealer> sealer) {
        this.secret = secret == null ? "" : secret.trim();
        this.checkIns = checkIns;
        this.reminders = reminders;
        this.staffAlerts = staffAlerts;
        this.escalation = escalation;
        this.sealer = sealer;
    }

    @PostMapping("/api/webhooks/tick")
    public Map<String, String> tick(@RequestHeader(value = "X-Khabar-Tick-Secret", required = false) String given) {
        if (secret.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (given == null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), given.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad secret.");
        }
        Map<String, String> ran = new LinkedHashMap<>();
        if (!running.compareAndSet(false, true)) {
            ran.put("status", "already running");
            return ran;
        }
        try {
            run(ran, "checkIns", () -> checkIns.ifAvailable(CheckInScheduler::run), checkIns.getIfAvailable() != null);
            run(ran, "reminders", () -> reminders.ifAvailable(RemindersScheduler::run), reminders.getIfAvailable() != null);
            run(ran, "staffAlerts", () -> staffAlerts.ifAvailable(StaffAlertScheduler::check), staffAlerts.getIfAvailable() != null);
            run(ran, "caseRouting", () -> escalation.ifAvailable(FollowUpCaseAutoEscalationScheduler::routeOverdueCases), escalation.getIfAvailable() != null);
            run(ran, "auditSeal", () -> sealer.ifAvailable(AuditChainSealer::seal), sealer.getIfAvailable() != null);
        } finally {
            running.set(false);
        }
        return ran;
    }

    private static void run(Map<String, String> ran, String name, Runnable job, boolean enabled) {
        if (!enabled) {
            ran.put(name, "off");
            return;
        }
        try {
            job.run();
            ran.put(name, "ok");
        } catch (RuntimeException e) {
            log.warn("Tick job {} failed: {}", name, e.getClass().getSimpleName());
            ran.put(name, "failed");
        }
    }
}
