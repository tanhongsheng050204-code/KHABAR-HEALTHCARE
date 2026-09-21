package com.khabar.api.followup;

import com.khabar.api.crypto.EncryptedStringConverter;
import com.khabar.api.patients.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A message the patient sent during follow-up, with the triage result. The text is stored encrypted. */
@Entity
@Table(name = "patient_reply")
public class PatientReply {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Patient patient;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "text_enc", nullable = false, length = 4096)
    private String text;

    @Column(nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriageLevel level;

    /** The word that triggered the level, if any. */
    private String matched;

    /** Set when a clinician has called the patient about it. */
    private Instant handledAt;

    private UUID handledBy;

    protected PatientReply() {
    }

    public PatientReply(Patient patient, String text, Instant receivedAt, TriageLevel level, String matched) {
        this.id = UUID.randomUUID();
        this.patient = patient;
        this.text = text;
        this.receivedAt = receivedAt;
        this.level = level;
        this.matched = matched;
    }

    public void markHandled(UUID clinicianId, Instant when) {
        this.handledBy = clinicianId;
        this.handledAt = when;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public String getText() {
        return text;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public TriageLevel getLevel() {
        return level;
    }

    public String getMatched() {
        return matched;
    }

    public Instant getHandledAt() {
        return handledAt;
    }
}
