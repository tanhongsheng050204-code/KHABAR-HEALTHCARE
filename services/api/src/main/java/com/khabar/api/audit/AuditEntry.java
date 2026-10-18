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

/**
 * One access to a patient's record. Rows are only ever added; the one later write is sealing, which links the
 * row into the tamper-evident chain (its position, the previous row's hash and its own hash) once.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(columnList = "patientId"),
        @Index(name = "ux_audit_log_chain_seq", columnList = "chain_seq", unique = true)
})
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

    /** Position in the chain, from 1. Null until sealed (rows written before the chain began stay null). */
    @Column(name = "chain_seq")
    private Long chainSeq;

    @Column(length = 64)
    private String prevHash;

    @Column(length = 64)
    private String hash;

    protected AuditEntry() {
    }

    public AuditEntry(UUID patientId, UUID actorId, String actorLabel, AuditAction action, Instant at) {
        this.patientId = patientId;
        this.actorId = actorId;
        this.actorLabel = actorLabel;
        this.action = action;
        this.at = at;
    }

    void seal(long seq, String prevHash, String hash) {
        if (this.hash != null) {
            throw new IllegalStateException("An audit entry is sealed once.");
        }
        this.chainSeq = seq;
        this.prevHash = prevHash;
        this.hash = hash;
    }

    public Long getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getActorId() {
        return actorId;
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

    public Long getChainSeq() {
        return chainSeq;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getHash() {
        return hash;
    }
}
