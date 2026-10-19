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
import java.util.Set;
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
    private final com.khabar.api.patients.PatientRepository patients;

    public AskKhabar(CurrentUser currentUser, ClinicStaffAccess staffAccess, FollowUpTable table, AgentClientService agents,
                     AiReceipts aiReceipts, AuditLog auditLog, ObjectMapper json, com.khabar.api.patients.PatientRepository patients) {
        this.patients = patients;
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
        String coded = codeNames(question, entries, patients.findByClinicId(doctor.getClinic().getId()));

        Answer answer = fromModel(coded, entries, byCode);
        if (answer == null) {
            answer = fromRules(question, entries);
        }
        for (Cited c : answer.patients()) {
            auditLog.record(doctor, c.patientId(), AuditAction.VIEWED_RECORD);
        }
        return answer;
    }

    /** Connectors in Malaysian names that are not names on their own. */
    static final Set<String> CONNECTORS = Set.of("bin", "binti", "bt", "bte", "al", "ap", "a/l", "a/p", "s/o", "d/o", "anak", "mr", "mrs", "ms", "dr");

    /**
     * No name reaches the AI. A followed patient's full name or any part of it becomes their code; the name of any
     * other patient at the clinic becomes [NAME]. Full names first, then single parts, longest first.
     */
    static String codeNames(String question, List<FollowUpTable.Entry> entries, List<Patient> clinicPatients) {
        Map<UUID, String> codes = new java.util.HashMap<>();
        entries.forEach(e -> codes.put(e.patient().getId(), e.code()));
        List<String[]> replacements = new ArrayList<>();
        for (Patient p : clinicPatients) {
            String full = p.getFullName() == null ? "" : p.getFullName().strip();
            if (full.isEmpty()) {
                continue;
            }
            String code = codes.getOrDefault(p.getId(), "[NAME]");
            replacements.add(new String[]{full, code});
            for (String part : full.split("\\s+")) {
                if (part.length() >= 3 && !CONNECTORS.contains(part.toLowerCase(Locale.ROOT))) {
                    replacements.add(new String[]{part, code});
                }
            }
        }
        replacements.sort(Comparator.comparingInt((String[] r) -> r[0].length()).reversed());
        String coded = question;
        for (String[] r : replacements) {
            // Letter-aware edges instead of \b, so names next to Chinese or Tamil text are found too.
            coded = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(r[0]) + "(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(coded).replaceAll(java.util.regex.Matcher.quoteReplacement(r[1]));
        }
        return coded;
    }

    @SuppressWarnings("unchecked")
    private Answer fromModel(String question, List<FollowUpTable.Entry> entries, Map<String, FollowUpTable.Entry> byCode) {
        List<Map<String, Object>> rows = entries.stream().map(FollowUpTable.Entry::row).toList();
        try {
            // Each patient's receipt holds only their own row: the question may be about someone else.
            for (FollowUpTable.Entry e : entries) {
                aiReceipts.text(e.patient(), AiReceipts.Purpose.ASK_KHABAR, "/agents/copilot/ask",
                        "A staff question about the clinic's follow-up. Your row was sent as " + e.code() + " with no name: "
                                + json.writeValueAsString(e.row()));
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

    /**
     * Khabar's own answer when no model is available. A condition named in the question ("diabetics", "blood
     * pressure") narrows who is considered; the topics ("missing doses", "missed visit", "slipping") decide who matches.
     */
    static Answer fromRules(String question, List<FollowUpTable.Entry> entries) {
        String q = question.toLowerCase(Locale.ROOT);
        List<String> population = new ArrayList<>();
        Predicate<FollowUpTable.Entry> among = e -> true;
        if (has(q, "diabet", "kencing manis")) {
            population.add("on the diabetes pathway");
            among = among.and(e -> onPathway(e, "DIABETES"));
        }
        if (has(q, "hypertens", "darah tinggi")) {
            population.add("on the high blood pressure pathway");
            among = among.and(e -> onPathway(e, "HYPERTENSION"));
        }
        List<String> topics = new ArrayList<>();
        Predicate<FollowUpTable.Entry> match = e -> false;
        if (has(q, "sugar", "glucose", "gula")) {
            topics.add("blood sugar rising or above target");
            match = match.or(e -> worse(e, "GLUCOSE") || reasonKey(e, "GLUCOSE_ABOVE_TARGET"));
        }
        if (has(q, "pressure", " bp", "bp ")) {
            topics.add("blood pressure rising or above target");
            match = match.or(e -> worse(e, "SYSTOLIC") || reasonKey(e, "BP_ABOVE_TARGET"));
        }
        if (has(q, "dose", "adheren", "ubat", "medicine", "taking", "pill", "tablet")) {
            topics.add("doses answered Missed or left unanswered this week");
            match = match.or(e -> reasonKey(e, "MISSED_DOSES") || reasonKey(e, "UNANSWERED_DOSES"));
        }
        if (has(q, "no-show", "no show", "appointment", "visit", "temu janji")) {
            topics.add("a missed booked visit");
            match = match.or(e -> reasonKey(e, "MISSED_VISIT"));
        }
        if (has(q, "hba1c", "a1c", "lab")) {
            topics.add("the latest HbA1c above target");
            match = match.or(e -> reasonKey(e, "HBA1C_ABOVE_TARGET"));
        }
        if (has(q, "review", "overdue")) {
            topics.add("a pathway review due");
            match = match.or(e -> reasonKey(e, "REVIEW_DUE"));
        }
        if (has(q, "quiet", "silent", "no reply", "not replied", "not answered")) {
            topics.add("no answer to a check-in for 48 hours");
            match = match.or(e -> reasonKey(e, "NO_REPLY"));
        }
        if (has(q, "understand", "plan check", "teach")) {
            topics.add("wrong answers in the last plan check");
            match = match.or(e -> reasonKey(e, "PLAN_NOT_UNDERSTOOD"));
        }
        if (topics.isEmpty() || has(q, "slip", "worse", "worst", "deteriorat", "risk", "concern", "worry", "priority")) {
            topics.add("readings worsening or a risk score of 5 or more");
            match = match.or(e -> worse(e, "GLUCOSE") || worse(e, "SYSTOLIC") || e.score().score() >= 5);
        }
        Predicate<FollowUpTable.Entry> wanted = among.and(match);
        long considered = entries.stream().filter(among).count();
        List<FollowUpTable.Entry> found = entries.stream().filter(wanted)
                .sorted(Comparator.comparingInt((FollowUpTable.Entry e) -> e.score().score()).reversed()).limit(10).toList();
        List<Cited> cited = found.stream().map(e -> new Cited(e.patient().getId(), e.patient().getFullName(),
                e.score().withoutValues().reasons().stream().map(r -> r.label()).reduce((a, b) -> a + "; " + b).orElse("no risk points"))).toList();
        String who = population.isEmpty() ? "followed patients" : "followed patients " + String.join(" and ", population);
        String text = found.isEmpty()
                ? "None of the " + considered + " " + who + " has " + String.join(", or ", topics) + "."
                : found.size() + " of " + considered + " " + who + " " + (found.size() == 1 ? "has " : "have ")
                + String.join(", or ", topics) + "; highest risk score first.";
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
