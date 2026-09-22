package com.khabar.api.encounters;

import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/** The patient's latest take-home summary: for the patient, a consented caregiver, or their clinic. */
@RestController
public class VisitSummaryController {

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final VisitSummaryRepository summaries;
    private final PatientAccessPolicy policy;
    private final AuditLog auditLog;

    public VisitSummaryController(CurrentUser currentUser, PatientRepository patients, VisitSummaryRepository summaries,
                                  PatientAccessPolicy policy, AuditLog auditLog) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.summaries = summaries;
        this.policy = policy;
        this.auditLog = auditLog;
    }

    public record SummaryView(UUID encounterId, String language, String text, String needsDoctor, Instant createdAt) {
    }

    @GetMapping("/api/patients/{patientId}/summary")
    @Transactional
    public SummaryView latest(@PathVariable UUID patientId, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!policy.canView(user, patient)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        VisitSummary summary = summaries.findFirstByPatientIdOrderByCreatedAtDesc(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No summary yet for this patient."));
        if (user.getRole() != Role.PATIENT) {
            auditLog.record(user, patientId, AuditAction.VIEWED_SUMMARY);
        }
        return new SummaryView(summary.getEncounterId(), summary.getLanguage(), summary.getText(), summary.getNeedsDoctor(), summary.getCreatedAt());
    }
}
