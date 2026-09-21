package com.khabar.api.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One access to a patient's record. Rows are only ever added, never changed. */
@Entity
@Table(name = "audit_log", indexes = @Index(columnList = "patientId"))
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private UUID actorId;

    /** How the actor appears to the patient, e.g. "Dr Priya · Klinik Dr Priya". Frozen at the time of access. */
    @Column(nullable = false)
    private String actorLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(nullable = false)
    private Instant at;

    protected AuditEntry() {
    }

    public AuditEntry(UUID patientId, UUID actorId, String actorLabel, AuditAction action, Instant at) {
        this.patientId = patientId;
        this.actorId = actorId;
        this.actorLabel = actorLabel;
        this.action = action;
        this.at = at;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public AuditAction getAction() {
        return action;
    }

    public Instant getAt() {
        return at;
    }
}
