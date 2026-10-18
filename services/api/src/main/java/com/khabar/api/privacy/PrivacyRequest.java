package com.khabar.api.privacy;

import com.khabar.api.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A patient's request under the PDPA: to erase their identity, or to correct their details. The clinic decides
 * and records what it did; the patient sees the outcome.
 */
@Entity
@Table(name = "privacy_request", indexes = @Index(name = "ix_privacy_request_clinic", columnList = "clinic_id"))
public class PrivacyRequest {

    /** ERASURE or CORRECTION. */
    @Column(nullable = false, length = 20)
    private String kind;

    /** OPEN, DONE or DECLINED. */
    @Column(nullable = false, length = 20)
    private String status;

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(nullable = false)
    private UUID patientId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "details_enc", length = 4096)
    private String details;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant resolvedAt;

    private UUID resolvedBy;

    @Column(length = 1000)
    private String resolution;

    protected PrivacyRequest() {
    }

    public PrivacyRequest(UUID clinicId, UUID patientId, String kind, String details, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.patientId = patientId;
        this.kind = kind;
        this.details = details;
        this.status = "OPEN";
        this.createdAt = createdAt;
    }

    void resolve(String status, String resolution, UUID by, Instant at) {
        this.status = status;
        this.resolution = resolution;
        this.resolvedBy = by;
        this.resolvedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getClinicId() { return clinicId; }
    public UUID getPatientId() { return patientId; }
    public String getKind() { return kind; }
    public String getDetails() { return details; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolution() { return resolution; }
}
