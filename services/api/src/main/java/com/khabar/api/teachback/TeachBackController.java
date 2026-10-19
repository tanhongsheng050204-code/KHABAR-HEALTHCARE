package com.khabar.api.teachback;

import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.encounters.Encounter;
import com.khabar.api.encounters.EncounterRepository;
import com.khabar.api.encounters.PrescriptionLine;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientAccessPolicy;
import com.khabar.api.patients.PatientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * "Check you understood your plan": up to three questions built from the doctor's own prescription and follow-up
 * date: how many times a day a medicine is taken, before or after food, and when the next visit is. No model writes
 * them. The patient sees the right answer straight after; the clinic sees who might need it explained again.
 */
@RestController
public class TeachBackController {

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final EncounterRepository encounters;
    private final TeachBackRepository checks;
    private final PatientAccessPolicy policy;
    private final AuditLog auditLog;
    private final AdjustableClock clock;

    public TeachBackController(CurrentUser currentUser, PatientRepository patients, EncounterRepository encounters,
                               TeachBackRepository checks, PatientAccessPolicy policy, AuditLog auditLog, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.encounters = encounters;
        this.checks = checks;
        this.policy = policy;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /** kind is TIMES_PER_DAY, FOOD or NEXT_VISIT; medicine names the medicine asked about, if any. */
    public record Question(String id, String kind, String medicine, List<String> options) {
    }

    record Asked(Question question, String answer) {
    }

    public record Quiz(UUID encounterId, List<Question> questions, List<Attempt> attempts) {
    }

    public record Answers(UUID encounterId, Map<String, String> answers) {
    }

    public record Result(String id, String kind, String medicine, String given, String answer, boolean correct) {
    }

    public record Outcome(int correct, int total, List<Result> results) {
    }

    public record Attempt(UUID id, int correct, int total, List<String> missed, java.time.Instant takenAt) {
    }

    @GetMapping("/api/me/teach-back")
    @Transactional(readOnly = true)
    public Quiz quiz(@AuthenticationPrincipal Jwt jwt) {
        Patient patient = ownRecord(jwt);
        Optional<Encounter> plan = latestPlan(patient);
        List<Question> questions = plan.map(TeachBackController::questions).orElse(List.of()).stream().map(Asked::question).toList();
        return new Quiz(plan.map(Encounter::getId).orElse(null), questions, attempts(patient.getId()));
    }

    @PostMapping("/api/me/teach-back")
    @Transactional
    public Outcome answer(@RequestBody Answers input, @AuthenticationPrincipal Jwt jwt) {
        Patient patient = ownRecord(jwt);
        Encounter plan = latestPlan(patient)
                .filter(e -> e.getId().equals(input.encounterId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Your plan has changed. Please start again."));
        Map<String, String> given = input.answers() == null ? Map.of() : input.answers();
        List<Result> results = new ArrayList<>();
        List<String> missed = new ArrayList<>();
        for (Asked asked : questions(plan)) {
            Question q = asked.question();
            String answer = given.get(q.id());
            boolean right = asked.answer().equals(answer);
            if (!right) {
                missed.add(q.id());
            }
            results.add(new Result(q.id(), q.kind(), q.medicine(), answer, asked.answer(), right));
        }
        if (results.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is nothing in your plan to check yet.");
        }
        int correct = results.size() - missed.size();
        checks.save(new TeachBack(patient.getId(), patient.getClinic().getId(), plan.getId(), results.size(), correct,
                missed.isEmpty() ? null : String.join(",", missed), clock.instant()));
        return new Outcome(correct, results.size(), results);
    }

    @GetMapping("/api/patients/{id}/teach-back")
    @Transactional
    public List<Attempt> forPatient(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Patient patient = patients.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!policy.isPatientOrTheirClinic(user, patient)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (user.getRole() != Role.PATIENT) {
            auditLog.record(user, patient.getId(), AuditAction.VIEWED_RECORD);
        }
        return attempts(patient.getId());
    }

    /** The questions for a plan, each with its right answer, in a fixed order so an answer always meets its question. */
    static List<Asked> questions(Encounter plan) {
        List<Asked> asked = new ArrayList<>();
        List<PrescriptionLine> regular = plan.getPrescription().stream().filter(rx -> !rx.isAsNeeded()).toList();
        for (int i = 0; i < regular.size(); i++) {
            PrescriptionLine rx = regular.get(i);
            Integer times = rx.getTimesPerDay() != null ? rx.getTimesPerDay() : rx.slots().isEmpty() ? null : rx.slots().size();
            if (times != null && times >= 1 && times <= 4) {
                asked.add(new Asked(new Question("times:" + i, "TIMES_PER_DAY", name(rx), List.of("1", "2", "3", "4")), times.toString()));
                break;
            }
        }
        for (int i = 0; i < regular.size(); i++) {
            PrescriptionLine rx = regular.get(i);
            if ("before_food".equals(rx.getTiming()) || "after_food".equals(rx.getTiming())) {
                asked.add(new Asked(new Question("food:" + i, "FOOD", name(rx), List.of("before_food", "after_food", "any")), rx.getTiming()));
                break;
            }
        }
        Double weeks = plan.getFollowUpWeeks();
        if (weeks != null && weeks > 0) {
            long right = Math.max(1, Math.round(weeks));
            Set<String> options = new LinkedHashSet<>();
            for (long w : new long[]{1, 2, 4, 8, 12}) {
                options.add(Long.toString(w));
            }
            options.add(Long.toString(right));
            List<String> sorted = options.stream().map(Long::parseLong).sorted().map(String::valueOf).toList();
            // The right answer and its nearest neighbours, so the choice is not obvious from the list.
            int at = sorted.indexOf(Long.toString(right));
            int from = Math.max(0, Math.min(at - 1, sorted.size() - 3));
            asked.add(new Asked(new Question("next_visit", "NEXT_VISIT", null, sorted.subList(from, from + 3)), Long.toString(right)));
        }
        return asked;
    }

    private static String name(PrescriptionLine rx) {
        return rx.getName() == null ? rx.getRaw() : rx.getName();
    }

    private Optional<Encounter> latestPlan(Patient patient) {
        return encounters.findFirstByPatientIdAndStatusOrderByFinalisedAtDesc(patient.getId(), Encounter.Status.FINAL);
    }

    private List<Attempt> attempts(UUID patientId) {
        return checks.findTop10ByPatientIdOrderByTakenAtDesc(patientId).stream()
                .map(c -> new Attempt(c.getId(), c.getCorrect(), c.getTotal(),
                        c.getMissed() == null ? List.of() : List.of(c.getMissed().split(",")), c.getTakenAt()))
                .toList();
    }

    private Patient ownRecord(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getRole() != Role.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The plan check is for the patient.");
        }
        return patients.findByAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No patient record is linked to this account."));
    }
}
