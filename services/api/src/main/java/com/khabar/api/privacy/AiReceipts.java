package com.khabar.api.privacy;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.patients.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "What the AI saw": a receipt for every time patient-related content leaves the clinical API for the AI
 * service. Recording never stops the work it describes: a failure is logged and the request carries on.
 */
@Service
public class AiReceipts {

    private static final Logger log = LoggerFactory.getLogger(AiReceipts.class);
    static final int MAX_SENT = 6000;

    public enum Purpose {
        INTAKE_CHAT("Check-in chat before the visit"),
        PREVISIT_REPORT("Pre-visit summary for the doctor"),
        DRAFT_REPORT("Doctor's notes turned into a draft report"),
        PATIENT_SUMMARY("Plan written in the patient's language"),
        REPLY_TRIAGE("Reading a check-in reply"),
        ANSWER_MATCH("Matching a reply to a clinic-approved answer"),
        PACKET_PHOTO("Reading a medicine packet photo"),
        DICTATION("Doctor's dictation turned into text"),
        VOICE_REPLY("Patient's voice note turned into text"),
        REPLY_GLOSS("English translation of a reply for clinic staff"),
        MEAL_PHOTO("Naming the food in a meal photo"),
        ASK_KHABAR("A staff question answered from coded follow-up data"),
        CALL_SCRIPT("Suggested questions for a follow-up call");

        private final String label;

        Purpose(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record ReceiptView(Long id, String purpose, String purposeLabel, String endpoint, String sent, boolean truncated,
                              boolean media, int namesRemoved, int icsRemoved, int phonesRemoved, java.time.Instant at) {
    }

    private final AiReceiptRepository receipts;
    private final AdjustableClock clock;
    private final TransactionTemplate ownTransaction;

    public AiReceipts(AiReceiptRepository receipts, AdjustableClock clock, PlatformTransactionManager transactions) {
        this.receipts = receipts;
        this.clock = clock;
        // Its own transaction: a receipt is kept even when the caller's transaction is read-only or rolls back.
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Text that was sent, exactly as sent (after identifiers were removed). */
    public void text(Patient patient, Purpose purpose, String endpoint, String sent) {
        String body = sent == null ? "" : sent;
        boolean truncated = body.length() > MAX_SENT;
        save(new AiReceipt(patient.getId(), purpose.name(), endpoint, truncated ? body.substring(0, MAX_SENT) : body,
                truncated, false, count(body, "[NAME]"), count(body, "[IC]"), count(body, "[PHONE]"), clock.instant()));
    }

    /** A chat history: each turn on its own line, the way the AI service received it. */
    public void conversation(Patient patient, Purpose purpose, String endpoint, List<Map<String, String>> messages) {
        StringBuilder out = new StringBuilder();
        for (Map<String, String> m : messages) {
            out.append("user".equals(m.get("role")) ? "Patient: " : "Khabar: ").append(m.get("content")).append('\n');
        }
        text(patient, purpose, endpoint, out.toString().strip());
    }

    /** A photo or recording: described, never stored. */
    public void media(Patient patient, Purpose purpose, String endpoint, String description) {
        save(new AiReceipt(patient.getId(), purpose.name(), endpoint, description, false, true, 0, 0, 0, clock.instant()));
    }

    public List<ReceiptView> latest(UUID patientId, int limit) {
        return receipts.findByPatientIdOrderByIdDesc(patientId, org.springframework.data.domain.PageRequest.of(0, limit)).stream()
                .map(AiReceipts::view)
                .toList();
    }

    static ReceiptView view(AiReceipt r) {
        String label;
        try {
            label = Purpose.valueOf(r.getPurpose()).label();
        } catch (IllegalArgumentException unknown) {
            label = r.getPurpose();
        }
        return new ReceiptView(r.getId(), r.getPurpose(), label, r.getEndpoint(), r.getSent(), r.isTruncated(), r.isMedia(),
                r.getNamesRemoved(), r.getIcsRemoved(), r.getPhonesRemoved(), r.getAt());
    }

    private void save(AiReceipt receipt) {
        try {
            ownTransaction.executeWithoutResult(status -> receipts.save(receipt));
        } catch (RuntimeException e) {
            log.warn("AI receipt not recorded ({}): {}", receipt.getPurpose(), e.getMessage());
        }
    }

    static int count(String text, String token) {
        int n = 0;
        for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + token.length())) {
            n++;
        }
        return n;
    }
}
