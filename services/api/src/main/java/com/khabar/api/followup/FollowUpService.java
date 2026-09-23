package com.khabar.api.followup;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.graph.PatientGraphSync;
import com.khabar.api.messaging.Messenger;
import com.khabar.api.messaging.PatientMessages;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.Redactor;
import com.khabar.api.service.AgentClientService;
import com.khabar.api.service.AgentDtos.AnswerOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A patient's reply, from the app or WhatsApp: triaged (identity removed first), stored encrypted,
 * and counted as the answer to their latest check-in. A reply is never lost: if triage fails, it
 * goes to a person. The patient always hears back, but only in words a doctor approved: a red flag
 * gets the fixed emergency advice, a question the clinic has an approved answer for gets that answer,
 * and anything else is acknowledged and left for a person.
 */
@Service
public class FollowUpService {

    private static final Logger log = LoggerFactory.getLogger(FollowUpService.class);

    private final PatientReplyRepository replies;
    private final CheckInRepository checkIns;
    private final ApprovedAnswerRepository answers;
    private final AgentClientService agents;
    private final PatientMessages messages;
    private final AdjustableClock clock;
    private final PatientGraphSync graphSync;

    public FollowUpService(PatientReplyRepository replies, CheckInRepository checkIns, ApprovedAnswerRepository answers,
                           AgentClientService agents, PatientMessages messages, AdjustableClock clock, PatientGraphSync graphSync) {
        this.replies = replies;
        this.checkIns = checkIns;
        this.answers = answers;
        this.agents = agents;
        this.messages = messages;
        this.clock = clock;
        this.graphSync = graphSync;
    }

    /** What happened to a reply: its triage level, and the approved answer the patient was sent, if any. */
    public record Outcome(TriageLevel level, String answer) {
    }

    @Transactional
    public Outcome receiveReply(Patient patient, String text) {
        String redacted = Redactor.redact(text, patient);
        TriageLevel level;
        String matched = null;
        boolean missedDose = false;
        try {
            Map<String, Object> result = agents.triageReply(redacted, patient.getGraphId().toString());
            level = TriageLevel.fromAgent(result == null ? null : result.get("level"));
            matched = result == null || result.get("matched") == null ? null : result.get("matched").toString();
            missedDose = result != null && Boolean.TRUE.equals(result.get("missed_dose"));
        } catch (RuntimeException e) {
            log.warn("Triage unavailable, sending reply to a person: {}", e.getMessage());
            level = TriageLevel.REVIEW;
        }
        PatientReply fresh = new PatientReply(patient, text, clock.instant(), level, matched);
        if (missedDose) {
            fresh.markMissedDose();
        }
        // save() merges (the id is set in the constructor), so keep the managed copy it returns
        PatientReply reply = replies.save(fresh);
        graphSync.changed(patient.getId());
        checkIns.findByPatientIdAndStatus(patient.getId(), CheckIn.Status.SENT).stream()
                .max(Comparator.comparing(CheckIn::getDueDate))
                .ifPresent(CheckIn::markAnswered);

        String language = patient.getPreferredLanguage();
        if (level == TriageLevel.RED) {
            messages.send(patient, PatientMessages.urgentText(language), Messenger.Kind.SAFETY);
            return new Outcome(level, null);
        }
        if (level == TriageLevel.OK || level == TriageLevel.REVIEW) {
            Optional<ApprovedAnswer> answer = approvedAnswerFor(patient, redacted);
            if (answer.isPresent()) {
                String words = answer.get().textFor(language);
                messages.send(patient, words, Messenger.Kind.ANSWER);
                reply.answeredWith(answer.get().getId(), clock.instant());
                return new Outcome(level, words);
            }
        }
        messages.send(patient, PatientMessages.acknowledgementText(language, level), Messenger.Kind.NOTICE);
        return new Outcome(level, null);
    }

    private Optional<ApprovedAnswer> approvedAnswerFor(Patient patient, String redacted) {
        List<ApprovedAnswer> offered = answers.findByClinicIdAndRetiredAtIsNullOrderByApprovedAt(patient.getClinic().getId()).stream()
                .filter(a -> a.textFor(patient.getPreferredLanguage()) != null)
                .toList();
        if (offered.isEmpty()) {
            return Optional.empty();
        }
        try {
            String id = agents.matchAnswer(redacted, offered.stream()
                    .map(a -> new AnswerOption(a.getId().toString(), a.getTitle(), List.copyOf(a.getTriggers())))
                    .toList());
            return offered.stream().filter(a -> a.getId().toString().equals(id)).findFirst();
        } catch (RuntimeException e) {
            log.warn("Answer matching unavailable, leaving the reply for a person: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
