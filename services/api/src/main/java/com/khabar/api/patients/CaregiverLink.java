package com.khabar.api.patients;

import com.khabar.api.identity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A patient's consent for a family member to see their information. Revoking keeps the row as a record. */
@Entity
@Table(name = "caregiver_link")
public class CaregiverLink {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Patient patient;

    @ManyToOne(optional = false)
    private AppUser caregiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaregiverScope scope;

    @Column(nullable = false)
    private Instant consentedAt;

    private Instant revokedAt;

    protected CaregiverLink() {
    }

    public CaregiverLink(Patient patient, AppUser caregiver, CaregiverScope scope) {
        this.id = UUID.randomUUID();
        this.patient = patient;
        this.caregiver = caregiver;
        this.scope = scope;
        this.consentedAt = Instant.now();
    }

    public void revoke(Instant when) {
        this.revokedAt = when;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public CaregiverScope getScope() {
        return scope;
    }
}
