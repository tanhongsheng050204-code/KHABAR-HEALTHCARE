package com.khabar.api.caregivers;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** One alert sent (or tried) to a caregiver's chat. Unique per caregiver and event, so nothing is sent twice. */
@Entity
@Table(name = "caregiver_notice",
        uniqueConstraints = @UniqueConstraint(name = "ux_caregiver_notice", columnNames = {"caregiver_id", "kind", "ref"}))
public class CaregiverNotice {

    @Id
    private UUID id;

    @Column(name = "caregiver_id", nullable = false)
    private UUID caregiverId;

    @Column(nullable = false)
    private UUID patientId;

    /** QUIET, MISSED_DOSES or URGENT_REPLY. */
    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false)
    private UUID ref;

    @Column(nullable = false)
    private Instant sentAt;

    private boolean delivered;

    protected CaregiverNotice() {
    }

    public CaregiverNotice(UUID caregiverId, UUID patientId, String kind, UUID ref, Instant sentAt, boolean delivered) {
        this.id = UUID.randomUUID();
        this.caregiverId = caregiverId;
        this.patientId = patientId;
        this.kind = kind;
        this.ref = ref;
        this.sentAt = sentAt;
        this.delivered = delivered;
    }

    public UUID getPatientId() { return patientId; }
    public String getKind() { return kind; }
    public Instant getSentAt() { return sentAt; }
    public boolean isDelivered() { return delivered; }
}
