package com.khabar.api.followup;

import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** "Call these patients today": the doctor's clinic, most urgent unhandled replies first, then patients who went quiet. */
@RestController
@RequestMapping("/api/clinic/call-list")
public class CallListController {

    private static final java.time.Duration NO_REPLY_AFTER = java.time.Duration.ofHours(48);
    private static final List<String> REASON_ORDER = List.of("REPLY", "MISSED_DOSE", "NO_REPLY");

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final PatientReplyRepository replies;
    private final AuditLog auditLog;
    private final AdjustableClock clock;
    private final CheckInRepository checkIns;

    public CallListController(CurrentUser currentUser, PatientRepository patients, PatientReplyRepository replies, AuditLog auditLog,
                              AdjustableClock clock, CheckInRepository checkIns) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.replies = replies;
        this.auditLog = auditLog;
        this.clock = clock;
        this.checkIns = checkIns;
    }

    /**
     * reason is REPLY (a reply needs a call), MISSED_DOSE (the patient's only news is a missed dose) or
     * NO_REPLY (a check-in has gone unanswered for 48 hours). Within a level, they rank in that order.
     */
    public record CallListItem(UUID patientId, String fullName, String preferredLanguage, TriageLevel level,
                               String urgentReply, String latestReply, Instant latestAt, Integer followUpDay,
                               int unhandledReplies, String reason) {
    }

    public record Counts(long red, long watch, long review) {
    }

    public record CallList(List<CallListItem> items, Counts counts, long patientsInFollowUp) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public CallList callList(@AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = requireDoctor(jwt);
        UUID clinicId = doctor.getClinic().getId();
        LocalDate today = LocalDate.now(clock);

        Map<Patient, List<PatientReply>> byPatient = replies.findByPatientClinicIdAndHandledAtIsNull(clinicId).stream()
                .filter(PatientReply::needsACall)
                .collect(Collectors.groupingBy(PatientReply::getPatient));

        List<CallListItem> fromReplies = byPatient.entrySet().stream()
                .map(e -> toItem(e.getKey(), e.getValue(), today))
                .toList();
        java.util.Set<UUID> listed = fromReplies.stream().map(CallListItem::patientId).collect(Collectors.toSet());
        Instant cutoff = clock.instant().minus(NO_REPLY_AFTER);
        List<CallListItem> silent = checkIns.findByPatientClinicIdAndStatusAndSentAtBefore(clinicId, CheckIn.Status.SENT, cutoff).stream()
                .filter(c -> !listed.contains(c.getPatient().getId()))
                .collect(Collectors.toMap(c -> c.getPatient().getId(), c -> c, (a, b) -> a.getSentAt().isBefore(b.getSentAt()) ? a : b))
                .values().stream()
                .map(c -> new CallListItem(c.getPatient().getId(), c.getPatient().getFullName(), c.getPatient().getPreferredLanguage(),
                        TriageLevel.REVIEW, null, null, c.getSentAt(), c.getPatient().followUpDay(today), 0, "NO_REPLY"))
                .toList();

        List<CallListItem> items = java.util.stream.Stream.concat(fromReplies.stream(), silent.stream())
                .sorted(Comparator.comparing(CallListItem::level)
                        .thenComparing(i -> REASON_ORDER.indexOf(i.reason()))
                        .thenComparing(CallListItem::latestAt, Comparator.reverseOrder()))
                .toList();

        Counts counts = new Counts(
                items.stream().filter(i -> i.level() == TriageLevel.RED).count(),
                items.stream().filter(i -> i.level() == TriageLevel.WATCH).count(),
                items.stream().filter(i -> i.level() == TriageLevel.REVIEW).count());
        return new CallList(items, counts, patients.countByClinicIdAndFollowUpStartIsNotNull(clinicId));
    }

    @PostMapping("/{patientId}/called")
    @Transactional
    public Map<String, Object> markCalled(@PathVariable UUID patientId, @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = requireDoctor(jwt);
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!patient.getClinic().getId().equals(doctor.getClinic().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        List<PatientReply> open = replies.findByPatientIdAndHandledAtIsNull(patientId);
        Instant now = clock.instant();
        open.forEach(r -> r.markHandled(doctor.getId(), now));
        List<CheckIn> unanswered = checkIns.findByPatientIdAndStatus(patientId, CheckIn.Status.SENT);
        unanswered.forEach(CheckIn::markAnswered);
        if (!open.isEmpty() || !unanswered.isEmpty()) {
            auditLog.record(doctor, patientId, AuditAction.CALLED_ABOUT_REPLY);
        }
        return Map.of("handledReplies", open.size());
    }

    private AppUser requireDoctor(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getRole() != Role.DOCTOR || user.getClinic() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The call list is for clinic doctors.");
        }
        return user;
    }

    private static CallListItem toItem(Patient patient, List<PatientReply> open, LocalDate today) {
        PatientReply latest = open.stream().max(Comparator.comparing(PatientReply::getReceivedAt)).orElseThrow();
        PatientReply urgent = open.stream()
                .min(Comparator.comparing(PatientReply::getLevel).thenComparing(PatientReply::getReceivedAt, Comparator.reverseOrder()))
                .orElseThrow();
        // A missed dose in an otherwise cheerful reply still needs a person, so it is never listed as OK.
        TriageLevel level = urgent.getLevel() == TriageLevel.OK ? TriageLevel.REVIEW : urgent.getLevel();
        boolean onlyMissedDoses = open.stream().allMatch(r -> r.isMissedDose() && r.getLevel().compareTo(TriageLevel.REVIEW) >= 0);
        return new CallListItem(patient.getId(), patient.getFullName(), patient.getPreferredLanguage(), level,
                urgent.getText(), latest.getText(), latest.getReceivedAt(), patient.followUpDay(today), open.size(),
                onlyMissedDoses ? "MISSED_DOSE" : "REPLY");
    }
}
