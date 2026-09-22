package com.khabar.api.followup;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.Redactor;
import com.khabar.api.service.AgentClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Map;

/**
 * A patient's reply, from the app or WhatsApp: triaged (identity removed first), stored encrypted,
 * and counted as the answer to their latest check-in. A reply is never lost: if triage fails, it
 * goes to a person.
 */
@Service
public class FollowUpService {

    private static final Logger log = LoggerFactory.getLogger(FollowUpService.class);

    private final PatientReplyRepository replies;
    private final CheckInRepository checkIns;
    private final AgentClientService agents;
    private final AdjustableClock clock;

    public FollowUpService(PatientReplyRepository replies, CheckInRepository checkIns, AgentClientService agents, AdjustableClock clock) {
        this.replies = replies;
        this.checkIns = checkIns;
        this.agents = agents;
        this.clock = clock;
    }

    @Transactional
    public TriageLevel receiveReply(Patient patient, String text) {
        TriageLevel level;
        String matched = null;
        try {
            Map<String, Object> result = agents.triageReply(Redactor.redact(text, patient));
            level = TriageLevel.fromAgent(result == null ? null : result.get("level"));
            matched = result == null || result.get("matched") == null ? null : result.get("matched").toString();
        } catch (RuntimeException e) {
            log.warn("Triage unavailable, sending reply to a person: {}", e.getMessage());
            level = TriageLevel.REVIEW;
        }
        replies.save(new PatientReply(patient, text, clock.instant(), level, matched));
        checkIns.findByPatientIdAndStatus(patient.getId(), CheckIn.Status.SENT).stream()
                .max(Comparator.comparing(CheckIn::getDueDate))
                .ifPresent(CheckIn::markAnswered);
        return level;
    }
}
