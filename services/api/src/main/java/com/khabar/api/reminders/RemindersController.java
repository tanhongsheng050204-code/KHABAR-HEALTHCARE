package com.khabar.api.reminders;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * A patient switches their own reminders on or off and answers dose reminders in the app; the clinic and
 * caregivers who share alerts see the last two weeks of answers.
 */
@RestController
public class RemindersController {

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final PatientAccessPolicy policy;
    private final AuditLog auditLog;
    private final Reminders reminders;

    public RemindersController(CurrentUser currentUser, PatientRepository patients, PatientAccessPolicy policy, AuditLog auditLog,
                               Reminders reminders) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.policy = policy;
        this.auditLog = auditLog;
        this.reminders = reminders;
    }

    public record SettingsInput(Boolean doseReminders, Boolean appointmentReminders) {
    }

    public record DoseAnswer(String response) {
    }

    @GetMapping("/api/me/reminders")
    @Transactional(readOnly = true)
    public Reminders.SettingsView mine(@AuthenticationPrincipal Jwt jwt) {
        return reminders.settingsFor(ownRecord(jwt));
    }

    @PutMapping("/api/me/reminders")
    @Transactional
    public Reminders.SettingsView update(@RequestBody SettingsInput input, @AuthenticationPrincipal Jwt jwt) {
        Patient patient = ownRecord(jwt);
        Reminders.SettingsView now = reminders.settingsFor(patient);
        return reminders.update(patient,
                input.doseReminders() == null ? now.doseReminders() : input.doseReminders(),
                input.appointmentReminders() == null ? now.appointmentReminders() : input.appointmentReminders());
    }

    @PostMapping("/api/me/doses/{id}")
    @Transactional
    public Reminders.Answer answer(@PathVariable UUID id, @RequestBody DoseAnswer input, @AuthenticationPrincipal Jwt jwt) {
        String response = input.response() == null ? "" : input.response().trim().toUpperCase(java.util.Locale.ROOT);
        if (!response.equals("TAKEN") && !response.equals("MISSED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer TAKEN or MISSED.");
        }
        Reminders.Answer answer = reminders.answerDose(ownRecord(jwt), id, response);
        if (!answer.recorded()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That reminder was already answered or is too old.");
        }
        return answer;
    }

    @GetMapping("/api/patients/{id}/adherence")
    @Transactional
    public Reminders.Adherence adherence(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Patient patient = patients.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!policy.canView(user, patient)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (user.getRole() != Role.PATIENT) {
            auditLog.record(user, patient.getId(), AuditAction.VIEWED_RECORD);
        }
        return reminders.adherence(patient.getId(), 14);
    }

    private Patient ownRecord(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getRole() != Role.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reminders are set by the patient.");
        }
        return patients.findByAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No patient record is linked to this account."));
    }
}
