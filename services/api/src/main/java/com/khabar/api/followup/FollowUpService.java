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
 * and counted as the answer to their latest check-in. Replies needing review enter the clinic queue;
 * this service does not notify staff. The patient hears only approved wording: a red label gets fixed
 * precautionary emergency advice, a question with an approved clinic answer gets that answer, and
 * anything else gets a notice that the clinic may not have seen it yet.
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

    /**
     * What happened to a reply: its triage level, the approved answer the patient was sent (if any), and
     * the words the patient was actually sent back, whichever kind they were.
     */
    public record Outcome(TriageLevel level, String answer, String message) {
    }

    @Transactional
    public Outcome receiveReply(Patient patient, String text) {
        String redacted = Redactor.redact(text, patient);
        TriageLevel level;
        String matched = null;
        boolean missedDose = false;
        boolean triaged = true;
        try {
            Map<String, Object> result = agents.triageReply(redacted, patient.getGraphId().toString());
            level = TriageLevel.fromAgent(result == null ? null : result.get("level"));
            matched = result == null || result.get("matched") == null ? null : result.get("matched").toString();
            missedDose = result != null && Boolean.TRUE.equals(result.get("missed_dose"));
        } catch (RuntimeException e) {
            log.warn("Triage unavailable, sending reply to a person: {}", e.getMessage());
            level = TriageLevel.REVIEW;
            triaged = false;
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
            String urgent = PatientMessages.urgentText(language);
            messages.send(patient, urgent, Messenger.Kind.SAFETY);
            return new Outcome(level, null, urgent);
        }
        if (level == TriageLevel.OK || level == TriageLevel.REVIEW) {
            Optional<ApprovedAnswer> answer = approvedAnswerFor(patient, redacted);
            if (answer.isPresent()) {
                String words = answer.get().textFor(language);
                messages.send(patient, words, Messenger.Kind.ANSWER);
                reply.answeredWith(answer.get().getId(), clock.instant());
                return new Outcome(level, words, words);
            }
        }
        String acknowledgement = triaged ? PatientMessages.acknowledgementText(language, level) : PatientMessages.uncheckedText(language);
        messages.send(patient, acknowledgement, Messenger.Kind.NOTICE);
        return new Outcome(level, null, acknowledgement);
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
