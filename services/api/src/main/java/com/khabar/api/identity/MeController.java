package com.khabar.api.identity;

import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final CurrentUser currentUser;
    private final PatientRepository patients;

    public MeController(CurrentUser currentUser, PatientRepository patients) {
        this.currentUser = currentUser;
        this.patients = patients;
    }

    /** patientId is set only for patients: the record their own account is linked to. */
    public record MeResponse(UUID id, Role role, String displayName, UUID clinicId, String clinicName, UUID patientId) {
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Clinic clinic = user.getClinic();
        UUID patientId = user.getRole() == Role.PATIENT
                ? patients.findByAccountId(user.getId()).map(Patient::getId).orElse(null)
                : null;
        return new MeResponse(user.getId(), user.getRole(), user.getDisplayName(),
                clinic == null ? null : clinic.getId(), clinic == null ? null : clinic.getName(), patientId);
    }
}
