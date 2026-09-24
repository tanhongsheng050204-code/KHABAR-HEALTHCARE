package com.khabar.api.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** An auditable, clinic-scoped staff permission grant; revocation preserves the history row. */
@Entity
@Table(name = "clinic_staff_grant",
        indexes = @Index(name = "ix_staff_grant_clinic_active", columnList = "clinic_id,revoked_at"),
        uniqueConstraints = @UniqueConstraint(name = "uk_staff_grant_active_key", columnNames = "active_key"))
public class ClinicStaffGrant {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private AppUser appUser;

    @ManyToOne(optional = false)
    private Clinic clinic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClinicStaffRole role;

    /** Null after revocation; uniqueness prevents two simultaneous active grants of the same role. */
    @Column(name = "active_key", length = 120)
    private String activeKey;

    @Column(nullable = false)
    private Instant grantedAt;

    private UUID grantedBy;

    private Instant revokedAt;

    private UUID revokedBy;

    protected ClinicStaffGrant() {
    }

    public ClinicStaffGrant(AppUser appUser, Clinic clinic, ClinicStaffRole role, UUID grantedBy, Instant grantedAt) {
        this.id = UUID.randomUUID();
        this.appUser = appUser;
        this.clinic = clinic;
        this.role = role;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
        this.activeKey = activeKey(appUser.getId(), clinic.getId(), role);
    }

    public void revoke(UUID actorId, Instant at) {
        if (revokedAt == null) {
            this.revokedBy = actorId;
            this.revokedAt = at;
            this.activeKey = null;
        }
    }

    public static String activeKey(UUID userId, UUID clinicId, ClinicStaffRole role) {
        return userId + ":" + clinicId + ":" + role.name();
    }

    public UUID getId() { return id; }
    public AppUser getAppUser() { return appUser; }
    public Clinic getClinic() { return clinic; }
    public ClinicStaffRole getRole() { return role; }
    public Instant getGrantedAt() { return grantedAt; }
    public UUID getGrantedBy() { return grantedBy; }
    public Instant getRevokedAt() { return revokedAt; }
    public UUID getRevokedBy() { return revokedBy; }
}
