package com.khabar.api.identity;

import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.CaregiverScope;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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

    /**
     * patientId is the patient's own record; patientScopes exposes each caregiver link's granted scope;
     * telegramLinked says whether the patient has linked the Khabar Telegram bot.
     */
    public record MeResponse(UUID id, Role role, String displayName, UUID clinicId, String clinicName, UUID patientId,
                             List<UUID> patientIds, Map<UUID, CaregiverScope> patientScopes,
                             List<ClinicStaffRole> clinicRoles, boolean telegramLinked) {
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Clinic clinic = user.getClinic();
        Patient own = user.getRole() == Role.PATIENT ? patients.findByAccountId(user.getId()).orElse(null) : null;
        UUID patientId = own == null ? null : own.getId();
        boolean telegramLinked = own != null && own.getTelegramChatId() != null;
        Map<UUID, CaregiverScope> patientScopes = new LinkedHashMap<>();
        if (user.getRole() == Role.CAREGIVER) {
            caregiverLinks.findByCaregiverIdAndRevokedAtIsNull(user.getId()).forEach(link -> {
                UUID linkedPatientId = link.getPatient().getId();
                patientScopes.merge(linkedPatientId, link.getScope(), (first, next) ->
                        first == CaregiverScope.SUMMARY_AND_ALERTS || next == CaregiverScope.SUMMARY_AND_ALERTS
                                ? CaregiverScope.SUMMARY_AND_ALERTS : CaregiverScope.SUMMARY);
            });
        }
        List<UUID> patientIds = List.copyOf(patientScopes.keySet());
        return new MeResponse(user.getId(), user.getRole(), user.getDisplayName(),
                clinic == null ? null : clinic.getId(), clinic == null ? null : clinic.getName(), patientId,
                patientIds, patientScopes,
                staffAccess.rolesFor(user), telegramLinked);
    }
}
