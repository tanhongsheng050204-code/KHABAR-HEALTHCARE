package com.khabar.api.identity;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Server-side authorization for clinic-scoped staff grants. */
@Component
public class ClinicStaffAccess {

    private final ClinicStaffGrantRepository grants;

    public ClinicStaffAccess(ClinicStaffGrantRepository grants) {
        this.grants = grants;
    }

    public boolean hasRole(AppUser user, ClinicStaffRole role) {
        if (user == null || user.getClinic() == null) return false;
        UUID clinicId = user.getClinic().getId();
        if (grants.existsByAppUserIdAndClinicId(user.getId(), clinicId)) {
            return grants.existsByAppUserIdAndClinicIdAndRoleAndRevokedAtIsNull(user.getId(), clinicId, role);
        }
        // Compatibility for local fixtures and pre-migration demo rows. The V3 migration backfills
        // persisted doctors, so a revoked pilot grant can never fall through this legacy path.
        return role == ClinicStaffRole.DOCTOR && user.getRole() == Role.DOCTOR;
    }

    public boolean canManageFollowUp(AppUser user) {
        return hasRole(user, ClinicStaffRole.DOCTOR) || hasRole(user, ClinicStaffRole.NURSE);
    }

    public boolean canManageStaff(AppUser user) {
        return hasRole(user, ClinicStaffRole.DOCTOR) || hasRole(user, ClinicStaffRole.CLINIC_ADMIN);
    }

    public boolean hasClinicalAccess(AppUser user) {
        // Nurse grants are deliberately limited to the follow-up queue and roster endpoints.
        // Opening a clinical record remains a doctor-only action.
        return hasRole(user, ClinicStaffRole.DOCTOR);
    }

    public List<ClinicStaffRole> rolesFor(AppUser user) {
        if (user == null || user.getClinic() == null) return List.of();
        UUID clinicId = user.getClinic().getId();
        List<ClinicStaffRole> active = grants.findByAppUserIdAndClinicIdAndRevokedAtIsNullOrderByGrantedAt(user.getId(), clinicId).stream()
                .map(ClinicStaffGrant::getRole)
                .distinct()
                .toList();
        if (!active.isEmpty()) return active;
        return !grants.existsByAppUserIdAndClinicId(user.getId(), clinicId) && user.getRole() == Role.DOCTOR
                ? List.of(ClinicStaffRole.DOCTOR) : List.of();
    }

    @Transactional
    public ClinicStaffGrant grant(AppUser user, Clinic clinic, ClinicStaffRole role, UUID grantedBy, Instant at) {
        return grants.findByAppUserIdAndClinicIdAndRoleAndRevokedAtIsNull(user.getId(), clinic.getId(), role)
                .orElseGet(() -> grants.save(new ClinicStaffGrant(user, clinic, role, grantedBy, at)));
    }
}
