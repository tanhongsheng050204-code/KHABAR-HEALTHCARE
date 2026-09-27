package com.khabar.api.followup;

import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.readings.Reading;
import com.khabar.api.readings.ReadingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private static final List<String> REASON_ORDER = List.of("REPLY", "READING", "MISSED_DOSE", "NO_REPLY", "OPEN_CASE");

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final PatientReplyRepository replies;
    private final AuditLog auditLog;
    private final AdjustableClock clock;
    private final CheckInRepository checkIns;
    private final ReadingRepository readings;
    private final ClinicStaffAccess staffAccess;
    private final FollowUpCases cases;
    private final com.khabar.api.clinicops.ClinicOps clinicOps;

    public CallListController(CurrentUser currentUser, PatientRepository patients, PatientReplyRepository replies, AuditLog auditLog,
                              AdjustableClock clock, CheckInRepository checkIns, ReadingRepository readings, ClinicStaffAccess staffAccess,
                              FollowUpCases cases, com.khabar.api.clinicops.ClinicOps clinicOps) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.replies = replies;
        this.auditLog = auditLog;
        this.clock = clock;
        this.checkIns = checkIns;
        this.readings = readings;
        this.staffAccess = staffAccess;
        this.cases = cases;
        this.clinicOps = clinicOps;
    }

    /**
     * reason is REPLY (a reply needs a call), READING (a home reading is out of range), MISSED_DOSE (the patient's
     * only news is a missed dose) or
     * NO_REPLY (a check-in has gone unanswered for 48 hours). Within a level, they rank in that order.
     */
    public record CallListItem(UUID patientId, String fullName, String preferredLanguage, TriageLevel level,
                               String urgentReply, String latestReply, Instant latestAt, Integer followUpDay,
                               int unhandledReplies, String reason, FollowUpCases.CaseView followUpCase) {

        CallListItem withCase(FollowUpCases.CaseView view) {
            return new CallListItem(patientId, fullName, preferredLanguage, view.level().compareTo(level) < 0 ? view.level() : level,
                    urgentReply, latestReply, latestAt, followUpDay, unhandledReplies, reason, view);
        }
    }

    public record Counts(long red, long watch, long review) {
    }

    /** Server timestamp for the exact queue snapshot rendered to the clinician. */
    public record CallList(List<CallListItem> items, Counts counts, long patientsInFollowUp, Instant snapshotAt,
                           com.khabar.api.clinicops.ClinicOps.Coverage coverage) {
    }

    public record ContactRequest(Instant observedThrough) {
    }

    /**
     * Listing a patient opens their follow-up case if they have none. A case stays on the list until someone
     * closes it, even when what put the patient there has since cleared.
     */
    @GetMapping
    @Transactional
    public CallList callList(@AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = requireFollowUpStaff(jwt);
        UUID clinicId = doctor.getClinic().getId();
        Instant snapshotAt = clock.instant();
        LocalDate today = LocalDate.ofInstant(snapshotAt, clock.getZone());

        Map<Patient, List<PatientReply>> byPatient = replies.findByPatientClinicIdAndHandledAtIsNull(clinicId).stream()
                .filter(reply -> !reply.getReceivedAt().isAfter(snapshotAt))
                .filter(PatientReply::needsACall)
                .collect(Collectors.groupingBy(PatientReply::getPatient));

        Map<UUID, CallListItem> byId = new java.util.LinkedHashMap<>();
        byPatient.forEach((patient, open) -> byId.put(patient.getId(), toItem(patient, open, today)));
        // A worrying home reading adds the patient, or replaces their reply item when it is more urgent.
        readings.findByPatientClinicIdAndHandledAtIsNullAndLevelNot(clinicId, TriageLevel.OK).stream()
                .filter(reading -> !reading.getWorkflowReceivedAt().isAfter(snapshotAt))
                .collect(Collectors.groupingBy(r -> r.getPatient().getId()))
                .forEach((id, open) -> {
                    CallListItem reading = readingItem(open, today);
                    CallListItem existing = byId.get(id);
                    if (existing == null || reading.level().compareTo(existing.level()) < 0) {
                        byId.put(id, reading);
                    }
                });
        List<CallListItem> fromReplies = List.copyOf(byId.values());
        java.util.Set<UUID> listed = byId.keySet();
        Instant cutoff = snapshotAt.minus(NO_REPLY_AFTER);
        List<CallListItem> silent = checkIns.findByPatientClinicIdAndStatusAndSentAtBefore(clinicId, CheckIn.Status.SENT, cutoff).stream()
                .filter(c -> !listed.contains(c.getPatient().getId()))
                .collect(Collectors.toMap(c -> c.getPatient().getId(), c -> c, (a, b) -> a.getSentAt().isBefore(b.getSentAt()) ? a : b))
                .values().stream()
                .map(c -> new CallListItem(c.getPatient().getId(), c.getPatient().getFullName(), c.getPatient().getPreferredLanguage(),
                        TriageLevel.REVIEW, null, null, c.getSentAt(), c.getPatient().followUpDay(today), 0, "NO_REPLY", null))
                .toList();

        com.khabar.api.clinicops.ClinicSettings settings = clinicOps.settingsFor(clinicId);
        Map<UUID, Patient> patientsById = new java.util.HashMap<>();
        byPatient.keySet().forEach(p -> patientsById.put(p.getId(), p));
        readings.findByPatientClinicIdAndHandledAtIsNullAndLevelNot(clinicId, TriageLevel.OK)
                .forEach(r -> patientsById.putIfAbsent(r.getPatient().getId(), r.getPatient()));
        checkIns.findByPatientClinicIdAndStatusAndSentAtBefore(clinicId, CheckIn.Status.SENT, cutoff)
                .forEach(c -> patientsById.putIfAbsent(c.getPatient().getId(), c.getPatient()));
        List<CallListItem> withCases = new java.util.ArrayList<>();
        for (CallListItem item : java.util.stream.Stream.concat(fromReplies.stream(), silent.stream()).toList()) {
            FollowUpCase open = cases.ensureOpen(patientsById.get(item.patientId()), item.level(), item.reason(), snapshotAt);
            withCases.add(item.withCase(cases.view(open, settings, snapshotAt)));
        }
        java.util.Set<UUID> shown = withCases.stream().map(CallListItem::patientId).collect(Collectors.toSet());
        cases.openCases(clinicId).stream()
                .filter(open -> !shown.contains(open.getPatient().getId()))
                .forEach(open -> withCases.add(new CallListItem(open.getPatient().getId(), open.getPatient().getFullName(),
                        open.getPatient().getPreferredLanguage(), open.getLevel(), null, null, open.getOpenedAt(),
                        open.getPatient().followUpDay(today), 0, "OPEN_CASE", cases.view(open, settings, snapshotAt))));

        List<CallListItem> items = withCases.stream()
                .sorted(Comparator.comparing(CallListItem::level)
                        .thenComparing(i -> REASON_ORDER.indexOf(i.reason()))
                        .thenComparing(CallListItem::latestAt, Comparator.reverseOrder()))
                .toList();

        Counts counts = new Counts(
                items.stream().filter(i -> i.level() == TriageLevel.RED).count(),
                items.stream().filter(i -> i.level() == TriageLevel.WATCH).count(),
                items.stream().filter(i -> i.level() == TriageLevel.REVIEW).count());
        return new CallList(items, counts, patients.countByClinicIdAndFollowUpStartIsNotNull(clinicId), snapshotAt,
                clinicOps.coverageToday(clinicId));
    }

    @PostMapping("/{patientId}/called")
    @Transactional
    public Map<String, Object> markCalled(@PathVariable UUID patientId, @RequestBody ContactRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = requireFollowUpStaff(jwt);
        if (request == null || request.observedThrough() == null || request.observedThrough().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh the call list before recording contact.");
        }
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!patient.getClinic().getId().equals(doctor.getClinic().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        FollowUpCases.Resolved resolved = cases.resolveSources(doctor, patientId, request.observedThrough(), clock.instant());
        cases.recordContact(doctor, patient, request.observedThrough());
        return Map.of("handledReplies", resolved.replies(), "handledReadings", resolved.readings(), "handledCheckIns", resolved.checkIns());
    }

    private AppUser requireFollowUpStaff(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (!staffAccess.canManageFollowUp(user) || user.getClinic() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The call list is for clinic doctors and nurses.");
        }
        return user;
    }

    private static CallListItem readingItem(List<Reading> open, LocalDate today) {
        Reading latest = open.stream().max(Comparator.comparing(Reading::getMeasuredAt)).orElseThrow();
        Reading urgent = open.stream()
                .min(Comparator.comparing(Reading::getLevel).thenComparing(Reading::getMeasuredAt, Comparator.reverseOrder()))
                .orElseThrow();
        Patient patient = urgent.getPatient();
        return new CallListItem(patient.getId(), patient.getFullName(), patient.getPreferredLanguage(), urgent.getLevel(),
                urgent.getDescription(), latest.getDescription(), latest.getMeasuredAt(), patient.followUpDay(today), open.size(), "READING", null);
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
                onlyMissedDoses ? "MISSED_DOSE" : "REPLY", null);
    }
}
