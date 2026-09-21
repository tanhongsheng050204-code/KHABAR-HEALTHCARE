package com.khabar.api.followup;

import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
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

/** "Call these patients today": the doctor's clinic, most urgent unhandled replies first. */
@RestController
@RequestMapping("/api/clinic/call-list")
public class CallListController {

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final PatientReplyRepository replies;
    private final AuditLog auditLog;

    public CallListController(CurrentUser currentUser, PatientRepository patients, PatientReplyRepository replies, AuditLog auditLog) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.replies = replies;
        this.auditLog = auditLog;
    }

    public record CallListItem(UUID patientId, String fullName, String preferredLanguage, TriageLevel level,
                               String urgentReply, String latestReply, Instant latestAt, Integer followUpDay,
                               int unhandledReplies) {
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
        LocalDate today = LocalDate.now();

        Map<Patient, List<PatientReply>> byPatient = replies.findByPatientClinicIdAndHandledAtIsNull(clinicId).stream()
                .filter(r -> r.getLevel().needsACall())
                .collect(Collectors.groupingBy(PatientReply::getPatient));

        List<CallListItem> items = byPatient.entrySet().stream()
                .map(e -> toItem(e.getKey(), e.getValue(), today))
                .sorted(Comparator.comparing(CallListItem::level).thenComparing(CallListItem::latestAt, Comparator.reverseOrder()))
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
        Instant now = Instant.now();
        open.forEach(r -> r.markHandled(doctor.getId(), now));
        if (!open.isEmpty()) {
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
        return new CallListItem(patient.getId(), patient.getFullName(), patient.getPreferredLanguage(), urgent.getLevel(),
                urgent.getText(), latest.getText(), latest.getReceivedAt(), patient.followUpDay(today), open.size());
    }
}
