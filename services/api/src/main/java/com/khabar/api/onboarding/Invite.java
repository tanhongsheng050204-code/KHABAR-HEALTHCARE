package com.khabar.api.onboarding;

import com.khabar.api.identity.Clinic;
import com.khabar.api.patients.CaregiverScope;
import com.khabar.api.patients.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-use code that lets a signed-in person become a patient (linking their own record),
 * a caregiver (with the patient's consent) or a doctor at a clinic. Only a hash of the code is stored.
 */
@Entity
@Table(name = "invite")
public class Invite {

    public enum Kind { PATIENT_ACCOUNT, CAREGIVER, DOCTOR }

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @ManyToOne
    private Clinic clinic;

    @ManyToOne
    private Patient patient;

    @Enumerated(EnumType.STRING)
    private CaregiverScope scope;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    private UUID usedBy;

    protected Invite() {
    }

    public Invite(String codeHash, Kind kind, Clinic clinic, Patient patient, CaregiverScope scope, UUID createdBy, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.codeHash = codeHash;
        this.kind = kind;
        this.clinic = clinic;
        this.patient = patient;
        this.scope = scope;
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
    }

    public boolean usable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(UUID by, Instant when) {
        this.usedBy = by;
        this.usedAt = when;
    }

    public Kind getKind() {
        return kind;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public Patient getPatient() {
        return patient;
    }

    public CaregiverScope getScope() {
        return scope;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
