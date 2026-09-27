package com.khabar.api.identity;

import com.khabar.api.config.AdjustableClock;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Staff management exposes identity and clinic grants only; it never returns patient records. */
@RestController
@RequestMapping("/api/clinic/staff")
public class ClinicStaffController {

    private final CurrentUser currentUser;
    private final ClinicStaffAccess staffAccess;
    private final ClinicStaffGrantRepository grants;
    private final AdjustableClock clock;
    private final com.khabar.api.clinicops.ClinicOps clinicOps;

    public ClinicStaffController(CurrentUser currentUser, ClinicStaffAccess staffAccess,
                                 ClinicStaffGrantRepository grants, AdjustableClock clock,
                                 com.khabar.api.clinicops.ClinicOps clinicOps) {
        this.currentUser = currentUser;
        this.staffAccess = staffAccess;
        this.grants = grants;
        this.clock = clock;
        this.clinicOps = clinicOps;
    }

    public record StaffView(UUID grantId, UUID userId, String displayName, ClinicStaffRole role,
                            Instant grantedAt, UUID grantedBy) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<StaffView> list(@AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireManager(jwt);
        return grants.findByClinicIdAndRevokedAtIsNullOrderByGrantedAt(actor.getClinic().getId()).stream()
                .map(grant -> new StaffView(grant.getId(), grant.getAppUser().getId(), grant.getAppUser().getDisplayName(),
                        grant.getRole(), grant.getGrantedAt(), grant.getGrantedBy()))
                .toList();
    }

    @DeleteMapping("/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void revoke(@PathVariable UUID grantId, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = requireManager(jwt);
        ClinicStaffGrant grant = grants.findByIdAndClinicIdAndRevokedAtIsNull(grantId, actor.getClinic().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That active staff grant was not found."));

        boolean isDoctor = staffAccess.hasRole(actor, ClinicStaffRole.DOCTOR);
        if (grant.getRole() == ClinicStaffRole.DOCTOR && !isDoctor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a clinic doctor can revoke a doctor's grant.");
        }
        if (grant.getRole() == ClinicStaffRole.CLINIC_ADMIN
                && grants.countByClinicIdAndRoleAndRevokedAtIsNull(actor.getClinic().getId(), ClinicStaffRole.CLINIC_ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A clinic must keep at least one active administrator.");
        }
        if (grant.getRole() == ClinicStaffRole.DOCTOR
                && grants.countByClinicIdAndRoleAndRevokedAtIsNull(actor.getClinic().getId(), ClinicStaffRole.DOCTOR) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A clinic must keep at least one active doctor.");
        }
        grant.revoke(actor.getId(), clock.instant());
        clinicOps.record(actor, com.khabar.api.clinicops.ClinicActivity.Action.STAFF_REVOKED,
                grant.getAppUser().getDisplayName() + " · " + grant.getRole().name().toLowerCase().replace('_', ' '));
    }

    private AppUser requireManager(Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (!staffAccess.canManageStaff(actor) || actor.getClinic() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only clinic doctors or administrators can manage clinic staff.");
        }
        return actor;
    }
}
