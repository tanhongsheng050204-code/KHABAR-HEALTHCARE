package com.khabar.api.clinicops;

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
 * One administrative or follow-up action in a clinic, for its administrators. The subject never names a
 * patient: cases appear by a short case reference, so an administrator without clinical access sees who did
 * what and when, not whose record it was. Rows are only ever added.
 */
@Entity
@Table(name = "clinic_activity", indexes = @Index(name = "ix_clinic_activity_clinic", columnList = "clinic_id"))
public class ClinicActivity {

    public enum Action {
        STAFF_INVITED, STAFF_REVOKED, SETTINGS_UPDATED,
        CASE_ASSIGNED, CASE_ACKNOWLEDGED, CASE_CONTACT_ATTEMPT, CASE_ESCALATED, CASE_CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(nullable = false)
    private UUID actorId;

    @Column(nullable = false)
    private String actorLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private Instant at;

    protected ClinicActivity() {
    }

    public ClinicActivity(UUID clinicId, UUID actorId, String actorLabel, Action action, String subject, Instant at) {
        this.clinicId = clinicId;
        this.actorId = actorId;
        this.actorLabel = actorLabel;
        this.action = action;
        this.subject = subject;
        this.at = at;
    }

    public String getActorLabel() { return actorLabel; }
    public Action getAction() { return action; }
    public String getSubject() { return subject; }
    public Instant getAt() { return at; }
}
