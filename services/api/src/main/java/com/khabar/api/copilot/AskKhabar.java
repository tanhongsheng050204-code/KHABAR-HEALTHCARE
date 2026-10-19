package com.khabar.api.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.insights.Trends;
import com.khabar.api.patients.Patient;
import com.khabar.api.privacy.AiReceipts;
import com.khabar.api.service.AgentClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * "Ask Khabar": a doctor asks about the clinic's follow-up in plain words ("which of my diabetics are slipping?").
 * The answer comes only from the clinic's own coded table: the AI sees P1, P2 … and never a name, and must cite the
 * rows it used, which the API turns back into patients. Without a model, Khabar's own rules answer. Read-only: it
 * never changes a record. Every row sent leaves an AI receipt, and every patient named in an answer is in that
 * patient's access history.
 */
@RestController
public class AskKhabar {

    private static final Logger log = LoggerFactory.getLogger(AskKhabar.class);
    static final int MAX_QUESTION = 500;

    public record Question(String question) {
    }

    public record Cited(UUID patientId, String fullName, String why) {
    }

    /** source is "model" or "rules". */
    public record Answer(String answer, String source, List<Cited> patients, int rowsConsidered) {
    }

    private final CurrentUser currentUser;
    private final ClinicStaffAccess staffAccess;
    private final FollowUpTable table;
    private final AgentClientService agents;
    private final AiReceipts aiReceipts;
    private final AuditLog auditLog;
    private final ObjectMapper json;

    public AskKhabar(CurrentUser currentUser, ClinicStaffAccess staffAccess, FollowUpTable table, AgentClientService agents,
                     AiReceipts aiReceipts, AuditLog auditLog, ObjectMapper json) {
        this.currentUser = currentUser;
        this.staffAccess = staffAccess;
        this.table = table;
        this.agents = agents;
        this.aiReceipts = aiReceipts;
        this.auditLog = auditLog;
        this.json = json;
    }

    @PostMapping("/api/clinic/ask")
    @Transactional
    public Answer ask(@RequestBody Question input, @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = currentUser.from(jwt);
        if (doctor.getClinic() == null || !staffAccess.hasClinicalAccess(doctor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ask Khabar is for the clinic's doctors.");
        }
        String question = input.question() == null ? "" : input.question().strip();
        if (question.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type a question.");
        }
        if (question.length() > MAX_QUESTION) {
            question = question.substring(0, MAX_QUESTION);
        }
        List<FollowUpTable.Entry> entries = table.build(doctor.getClinic().getId());
        if (entries.isEmpty()) {
            return new Answer("No patients are in follow-up or on a pathway yet, so there is nothing to answer from.", "rules", List.of(), 0);
        }
        Map<String, FollowUpTable.Entry> byCode = new LinkedHashMap<>();
        entries.forEach(e -> byCode.put(e.code(), e));
        String coded = codeNames(question, entries);

        Answer answer = fromModel(coded, entries, byCode);
        if (answer == null) {
            answer = fromRules(question, entries);
        }
        for (Cited c : answer.patients()) {
            auditLog.record(doctor, c.patientId(), AuditAction.VIEWED_RECORD);
        }
        return answer;
    }

    /** A patient named in the question becomes their code, so no name reaches the AI. */
    static String codeNames(String question, List<FollowUpTable.Entry> entries) {
        String coded = question;
        for (FollowUpTable.Entry e : entries) {
            String full = e.patient().getFullName();
            coded = Pattern.compile("\\b" + Pattern.quote(full) + "\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(coded).replaceAll(e.code());
            String first = full.split("\\s+")[0];
            if (first.length() >= 4) {
                coded = Pattern.compile("\\b" + Pattern.quote(first) + "\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(coded).replaceAll(e.code());
            }
        }
        return coded;
    }

    @SuppressWarnings("unchecked")
    private Answer fromModel(String question, List<FollowUpTable.Entry> entries, Map<String, FollowUpTable.Entry> byCode) {
        List<Map<String, Object>> rows = entries.stream().map(FollowUpTable.Entry::row).toList();
        try {
            for (FollowUpTable.Entry e : entries) {
                aiReceipts.text(e.patient(), AiReceipts.Purpose.ASK_KHABAR, "/agents/copilot/ask",
                        "Staff question: " + question + "\nThis patient's row (as " + e.code() + "): " + json.writeValueAsString(e.row()));
            }
            Map<String, Object> result = agents.askKhabar(question, rows);
            Object text = result == null ? null : result.get("answer");
            if (!(text instanceof String answer) || answer.isBlank()) {
                return null;
            }
            List<Cited> cited = new ArrayList<>();
            if (result.get("patients") instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m && byCode.containsKey(String.valueOf(m.get("code")))) {
                        Patient p = byCode.get(String.valueOf(m.get("code"))).patient();
                        cited.add(new Cited(p.getId(), p.getFullName(), String.valueOf(m.get("why"))));
                    }
                }
            }
            // The model writes codes; staff read names.
            String named = answer;
            for (FollowUpTable.Entry e : entries) {
                named = named.replaceAll("\\b" + Pattern.quote(e.code()) + "\\b", java.util.regex.Matcher.quoteReplacement(e.patient().getFullName()));
            }
            return new Answer(named, "model", cited, entries.size());
        } catch (Exception e) {
            log.info("Ask Khabar model unavailable, answering with rules: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /** Khabar's own answer when no model is available: keyword topics matched against the same table. */
    static Answer fromRules(String question, List<FollowUpTable.Entry> entries) {
        String q = question.toLowerCase(Locale.ROOT);
        List<String> topics = new ArrayList<>();
        Predicate<FollowUpTable.Entry> match = e -> false;
        if (has(q, "diabet", "sugar", "glucose", "gula", "kencing manis")) {
            topics.add("on the diabetes pathway with blood sugar rising or above target");
            match = match.or(e -> onPathway(e, "DIABETES") && (worse(e, "GLUCOSE") || reasonKey(e, "GLUCOSE_ABOVE_TARGET")));
        }
        if (has(q, "pressure", "bp", "hypertens", "darah tinggi")) {
            topics.add("with blood pressure rising or above target");
            match = match.or(e -> worse(e, "SYSTOLIC") || reasonKey(e, "BP_ABOVE_TARGET"));
        }
        if (has(q, "miss", "dose", "adheren", "ubat", "medicine", "taking")) {
            topics.add("who answered Missed or left dose reminders unanswered this week");
            match = match.or(e -> reasonKey(e, "MISSED_DOSES") || reasonKey(e, "UNANSWERED_DOSES"));
        }
        if (has(q, "no-show", "no show", "appointment", "visit", "temu janji")) {
            topics.add("who missed a booked visit");
            match = match.or(e -> reasonKey(e, "MISSED_VISIT"));
        }
        if (has(q, "hba1c", "a1c", "lab")) {
            topics.add("with the latest HbA1c above target");
            match = match.or(e -> reasonKey(e, "HBA1C_ABOVE_TARGET"));
        }
        if (has(q, "review", "overdue")) {
            topics.add("whose pathway review is due");
            match = match.or(e -> reasonKey(e, "REVIEW_DUE"));
        }
        if (has(q, "quiet", "silent", "no reply", "not replied", "reply")) {
            topics.add("who have not answered a check-in for 48 hours");
            match = match.or(e -> reasonKey(e, "NO_REPLY"));
        }
        if (has(q, "understand", "plan check", "teach")) {
            topics.add("whose last plan check had wrong answers");
            match = match.or(e -> reasonKey(e, "PLAN_NOT_UNDERSTOOD"));
        }
        if (topics.isEmpty() || has(q, "slip", "worse", "worst", "deteriorat", "risk", "concern", "worry", "priority")) {
            topics.add("with the highest follow-up risk scores");
            match = match.or(e -> e.score().score() >= 3);
        }
        List<FollowUpTable.Entry> found = entries.stream().filter(match)
                .sorted(Comparator.comparingInt((FollowUpTable.Entry e) -> e.score().score()).reversed()).limit(10).toList();
        List<Cited> cited = found.stream().map(e -> new Cited(e.patient().getId(), e.patient().getFullName(),
                e.score().withoutValues().reasons().stream().map(r -> r.label()).reduce((a, b) -> a + "; " + b).orElse("no risk points"))).toList();
        String text = found.isEmpty()
                ? "No patient in follow-up matches: " + String.join("; ", topics) + "."
                : found.size() + " of " + entries.size() + " followed patients " + String.join(", or ", topics) + ", highest score first.";
        return new Answer(text + " (Answered by Khabar's rules; no AI model is set up.)", "rules", cited, entries.size());
    }

    private static boolean has(String q, String... words) {
        for (String w : words) {
            if (q.contains(w)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean onPathway(FollowUpTable.Entry e, String key) {
        return ((List<String>) e.row().get("pathways")).contains(key);
    }

    private static boolean worse(FollowUpTable.Entry e, String key) {
        return e.trends().series().stream().anyMatch(s -> s.key().equals(key) && Trends.WORSENING.equals(s.direction()));
    }

    private static boolean reasonKey(FollowUpTable.Entry e, String key) {
        return e.score().reasons().stream().anyMatch(r -> r.key().equals(key));
    }
}
