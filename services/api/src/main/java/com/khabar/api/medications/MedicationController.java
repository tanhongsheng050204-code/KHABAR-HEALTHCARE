package com.khabar.api.medications;

import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.config.AdjustableClock;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * "What I take": the patient, a consented caregiver or the clinic keeps this list, and the
 * doctor's safety check compares every new prescription against it.
 */
@RestController
@RequestMapping("/api/patients/{patientId}/medications")
public class MedicationController {

    private static final int MAX_LENGTH = 200;

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final PatientAccessPolicy policy;
    private final MedicationItemRepository items;
    private final AuditLog auditLog;
    private final AdjustableClock clock;

    public MedicationController(CurrentUser currentUser, PatientRepository patients, PatientAccessPolicy policy,
                                MedicationItemRepository items, AuditLog auditLog, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.policy = policy;
        this.items = items;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    public record AddRequest(String name, MedicationItem.Kind kind, String source) {
    }

    public record ItemView(UUID id, String name, MedicationItem.Kind kind, String source, Role addedBy, Instant addedAt) {
        static ItemView of(MedicationItem item) {
            return new ItemView(item.getId(), item.getName(), item.getKind(), item.getSource(), item.getAddedByRole(), item.getAddedAt());
        }
    }

    @GetMapping
    @Transactional
    public List<ItemView> list(@PathVariable UUID patientId, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Patient patient = allowed(user, patientId);
        if (user.getRole() != Role.PATIENT) {
            auditLog.record(user, patient.getId(), AuditAction.VIEWED_MEDICATIONS);
        }
        return items.findByPatientIdAndStoppedAtIsNullOrderByAddedAt(patient.getId()).stream().map(ItemView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ItemView add(@PathVariable UUID patientId, @RequestBody AddRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Patient patient = allowed(user, patientId);
        String name = trimmed(request.name());
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say what the medicine or remedy is called.");
        }
        if (name.length() > MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That name is too long.");
        }
        MedicationItem.Kind kind = request.kind() == null ? MedicationItem.Kind.MEDICINE : request.kind();
        String source = trimmed(request.source());
        if (source != null && source.length() > MAX_LENGTH) {
            source = source.substring(0, MAX_LENGTH);
        }
        return ItemView.of(items.save(new MedicationItem(patient, name, kind, source, user.getRole(), user.getId(), clock.instant())));
    }

    @DeleteMapping("/{itemId}")
    @Transactional
    public ItemView stop(@PathVariable UUID patientId, @PathVariable UUID itemId, @AuthenticationPrincipal Jwt jwt) {
        Patient patient = allowed(currentUser.from(jwt), patientId);
        MedicationItem item = items.findById(itemId)
                .filter(i -> i.getPatient().getId().equals(patient.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        item.stop(clock.instant());
        return ItemView.of(item);
    }

    private Patient allowed(AppUser user, UUID patientId) {
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!policy.canView(user, patient)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return patient;
    }

    private static String trimmed(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
