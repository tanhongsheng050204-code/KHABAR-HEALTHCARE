package com.khabar.api.patients;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** The clinic's patient list for its doctors: names and follow-up day only. Opening a record is what gets audited. */
@RestController
public class ClinicPatientsController {

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final AdjustableClock clock;

    public ClinicPatientsController(CurrentUser currentUser, PatientRepository patients, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.clock = clock;
    }

    public record PatientRow(UUID id, String fullName, String icMasked, String preferredLanguage, Integer followUpDay, boolean hasAccount) {
    }

    @GetMapping("/api/clinic/patients")
    @Transactional(readOnly = true)
    public List<PatientRow> list(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getRole() != Role.DOCTOR || user.getClinic() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The patient list is for clinic doctors.");
        }
        LocalDate today = LocalDate.now(clock);
        return patients.findByClinicId(user.getClinic().getId()).stream()
                .sorted(Comparator.comparing(Patient::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> new PatientRow(p.getId(), p.getFullName(), p.icMasked(), p.getPreferredLanguage(),
                        p.getFollowUpStart() == null ? null : p.followUpDay(today), p.getAccount() != null))
                .toList();
    }
}
