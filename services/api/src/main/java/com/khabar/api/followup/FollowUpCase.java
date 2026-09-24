package com.khabar.api.followup;

import com.khabar.api.crypto.EncryptedStringConverter;
import com.khabar.api.identity.Clinic;
import com.khabar.api.patients.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One patient's open need for a person to follow up, from the moment it reaches the call list until someone
 * closes it with a reason. A case never disappears because a list was refreshed or its source cleared; only
 * an explicit closure ends it. At most one case per patient is open at a time (openKey is unique while open).
 */
@Entity
@Table(name = "follow_up_case", indexes = @Index(name = "ix_case_clinic_closed", columnList = "clinic_id,closed_at"))
public class FollowUpCase {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Clinic clinic;

    @ManyToOne(optional = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status;

    /** The most urgent level seen while open; it can rise, never fall. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriageLevel level;

    /** What first put the patient on the list: REPLY, READING, MISSED_DOSE or NO_REPLY. */
    @Column(nullable = false, length = 20)
    private String reason;

    /** clinic:patient while open, null once closed, so the database refuses a second open case. */
    @Column(name = "open_key", length = 80, unique = true)
    private String openKey;

    private UUID ownerId;

    @Column(nullable = false)
    private Instant openedAt;

    private Instant acknowledgedAt;

    private Instant escalatedAt;

    private UUID escalatedTo;

    @Column(nullable = false)
    private int contactAttempts;

    private Instant lastAttemptAt;

    private Instant closedAt;

    private UUID closedBy;

    @Enumerated(EnumType.STRING)
    private ClosureReason closureReason;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "closure_note_enc", length = 4096)
    private String closureNote;

    protected FollowUpCase() {
    }

    public FollowUpCase(Clinic clinic, Patient patient, TriageLevel level, String reason, Instant openedAt) {
        this.id = UUID.randomUUID();
        this.clinic = clinic;
        this.patient = patient;
        this.level = level;
        this.reason = reason;
        this.openedAt = openedAt;
        this.status = CaseStatus.NEW;
        this.openKey = openKey(clinic.getId(), patient.getId());
    }

    public static String openKey(UUID clinicId, UUID patientId) {
        return clinicId + ":" + patientId;
    }

    public boolean isOpen() {
        return closedAt == null;
    }

    /** True when the level became more urgent. */
    boolean raiseTo(TriageLevel newLevel) {
        if (newLevel.compareTo(level) < 0) {
            level = newLevel;
            return true;
        }
        return false;
    }

    void assign(UUID owner) {
        ownerId = owner;
        if (status == CaseStatus.NEW) {
            status = CaseStatus.ASSIGNED;
        }
    }

    /** True the first time only. */
    boolean acknowledge(UUID actor, Instant at) {
        if (acknowledgedAt != null) {
            return false;
        }
        acknowledgedAt = at;
        if (ownerId == null) {
            ownerId = actor;
        }
        if (status == CaseStatus.NEW || status == CaseStatus.ASSIGNED) {
            status = CaseStatus.ACKNOWLEDGED;
        }
        return true;
    }

    void attempt(boolean reached, UUID actor, Instant at) {
        acknowledge(actor, at);
        contactAttempts++;
        lastAttemptAt = at;
        if (status != CaseStatus.ESCALATED || reached) {
            status = reached ? CaseStatus.IN_PROGRESS : CaseStatus.UNABLE_TO_CONTACT;
        }
    }

    void escalate(UUID to, UUID actor, Instant at) {
        acknowledge(actor, at);
        escalatedAt = at;
        escalatedTo = to;
        status = CaseStatus.ESCALATED;
    }

    void close(ClosureReason reason, String note, UUID actor, Instant at) {
        acknowledge(actor, at);
        closureReason = reason;
        closureNote = note;
        closedBy = actor;
        closedAt = at;
        status = CaseStatus.RESOLVED;
        openKey = null;
    }

    public UUID getId() { return id; }
    public Clinic getClinic() { return clinic; }
    public Patient getPatient() { return patient; }
    public CaseStatus getStatus() { return status; }
    public TriageLevel getLevel() { return level; }
    public String getReason() { return reason; }
    public UUID getOwnerId() { return ownerId; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public UUID getEscalatedTo() { return escalatedTo; }
    public int getContactAttempts() { return contactAttempts; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public Instant getClosedAt() { return closedAt; }
    public UUID getClosedBy() { return closedBy; }
    public ClosureReason getClosureReason() { return closureReason; }
    public String getClosureNote() { return closureNote; }
}
