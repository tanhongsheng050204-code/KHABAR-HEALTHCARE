package com.khabar.api.identity;

import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final CaregiverLinkRepository caregiverLinks;
    private final ClinicStaffAccess staffAccess;

    public MeController(CurrentUser currentUser, PatientRepository patients, CaregiverLinkRepository caregiverLinks,
                        ClinicStaffAccess staffAccess) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.caregiverLinks = caregiverLinks;
        this.staffAccess = staffAccess;
    }

    /** patientId is the patient's own record. patientIds are active records a caregiver has consent to open. */
    public record MeResponse(UUID id, Role role, String displayName, UUID clinicId, String clinicName, UUID patientId,
                             List<UUID> patientIds, List<ClinicStaffRole> clinicRoles) {
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Clinic clinic = user.getClinic();
        UUID patientId = user.getRole() == Role.PATIENT
                ? patients.findByAccountId(user.getId()).map(Patient::getId).orElse(null)
                : null;
        List<UUID> patientIds = user.getRole() == Role.CAREGIVER
                ? caregiverLinks.findByCaregiverIdAndRevokedAtIsNull(user.getId()).stream()
                    .map(link -> link.getPatient().getId()).toList()
                : List.of();
        return new MeResponse(user.getId(), user.getRole(), user.getDisplayName(),
                clinic == null ? null : clinic.getId(), clinic == null ? null : clinic.getName(), patientId, patientIds,
                staffAccess.rolesFor(user));
    }
}
