package com.khabar.api.patients;

import com.khabar.api.privacy.AiReceipts;
import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final PatientAccessPolicy policy;
    private final AuditLog auditLog;
    private final AiReceipts aiReceipts;

    public PatientController(CurrentUser currentUser, PatientRepository patients, PatientAccessPolicy policy, AuditLog auditLog, AiReceipts aiReceipts) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.policy = policy;
        this.auditLog = auditLog;
        this.aiReceipts = aiReceipts;
    }

    public record PatientView(UUID id, String fullName, String icMasked, String preferredLanguage, String clinicName) {
    }

    public record AccessLogEntry(Instant at, String actor, AuditAction action) {
    }

    @GetMapping("/{id}")
    @Transactional
    public PatientView view(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Patient patient = find(id);
        if (!policy.canView(user, patient)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (user.getRole() != Role.PATIENT) {
            auditLog.record(user, patient.getId(), AuditAction.VIEWED_RECORD);
        }
        return new PatientView(patient.getId(), patient.getFullName(), patient.icMasked(),
                patient.getPreferredLanguage(), patient.getClinic().getName());
    }

    @GetMapping("/{id}/access-log")
    @Transactional(readOnly = true)
    public List<AccessLogEntry> accessLog(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Patient patient = find(id);
        if (!policy.canReadAccessLog(user, patient)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return auditLog.forPatient(patient.getId()).stream()
                .map(e -> new AccessLogEntry(e.getAt(), e.getActorLabel(), e.getAction()))
                .toList();
    }

    /** "What the AI saw": what left the clinical API for the AI service about this patient, newest first. */
    @GetMapping("/{id}/ai-receipts")
    @Transactional
    public List<AiReceipts.ReceiptView> aiReceipts(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Patient patient = find(id);
        if (!policy.isPatientOrTheirClinic(user, patient)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (user.getRole() != Role.PATIENT) {
            // Receipts hold the de-identified text of the patient's messages: reading them is reading the record.
            auditLog.record(user, patient.getId(), AuditAction.VIEWED_RECORD);
        }
        return aiReceipts.latest(patient.getId(), 50);
    }

    private Patient find(UUID id) {
        return patients.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
