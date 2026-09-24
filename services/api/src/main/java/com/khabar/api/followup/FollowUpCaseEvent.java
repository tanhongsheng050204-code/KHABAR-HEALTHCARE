package com.khabar.api.followup;

import com.khabar.api.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/** One step in a case's history. Rows are only ever added. */
@Entity
@Table(name = "follow_up_case_event", indexes = @Index(name = "ix_case_event_case", columnList = "case_id"))
public class FollowUpCaseEvent {

    public enum Action { OPENED, LEVEL_RAISED, ASSIGNED, ACKNOWLEDGED, CONTACT_REACHED, CONTACT_NO_ANSWER, ESCALATED, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    private CaseStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus toStatus;

    /** Null for events Khabar recorded itself (a case opening or its level rising). */
    private UUID actorId;

    @Column(nullable = false)
    private String actorLabel;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "note_enc", length = 4096)
    private String note;

    @Column(nullable = false)
    private Instant at;

    protected FollowUpCaseEvent() {
    }

    public FollowUpCaseEvent(UUID caseId, Action action, CaseStatus fromStatus, CaseStatus toStatus, UUID actorId,
                             String actorLabel, String note, Instant at) {
        this.caseId = caseId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.actorLabel = actorLabel;
        this.note = note;
        this.at = at;
    }

    public Action getAction() { return action; }
    public CaseStatus getFromStatus() { return fromStatus; }
    public CaseStatus getToStatus() { return toStatus; }
    public UUID getActorId() { return actorId; }
    public String getActorLabel() { return actorLabel; }
    public String getNote() { return note; }
    public Instant getAt() { return at; }
}
