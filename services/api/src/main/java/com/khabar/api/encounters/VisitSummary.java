package com.khabar.api.encounters;

import com.khabar.api.crypto.EncryptedStringConverter;
import com.khabar.api.patients.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** The plain-language summary a patient takes home from a visit, in their language. */
@Entity
@Table(name = "visit_summary")
public class VisitSummary {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Patient patient;

    @Column(nullable = false)
    private UUID encounterId;

    @Column(nullable = false)
    private String language;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "text_enc", nullable = false, length = 16384)
    private String text;

    /** Medicines the summary could not safely schedule (e.g. three doses a day while fasting). */
    @Column(length = 2000)
    private String needsDoctor;

    @Column(nullable = false)
    private Instant createdAt;

    /** Why the independent plan check held this summary back; null when it passed. */
    @Column(length = 2000)
    private String heldReason;

    private Instant releasedAt;

    private UUID releasedBy;

    @Column(length = 500)
    private String releaseNote;

    protected VisitSummary() {
    }

    public VisitSummary(Patient patient, UUID encounterId, String language, String text, String needsDoctor, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.patient = patient;
        this.encounterId = encounterId;
        this.language = language;
        this.text = text;
        this.needsDoctor = needsDoctor;
        this.createdAt = createdAt;
    }

    void hold(String reason) {
        this.heldReason = reason.length() > 2000 ? reason.substring(0, 2000) : reason;
    }

    void release(UUID by, String note, Instant at) {
        this.releasedBy = by;
        this.releaseNote = note;
        this.releasedAt = at;
    }

    /** Held back and not yet released by a doctor: not sent, and not shown to the patient or caregivers. */
    public boolean isHeld() {
        return heldReason != null && releasedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public String getHeldReason() {
        return heldReason;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public String getReleaseNote() {
        return releaseNote;
    }

    public UUID getEncounterId() {
        return encounterId;
    }

    public String getLanguage() {
        return language;
    }

    public String getText() {
        return text;
    }

    public String getNeedsDoctor() {
        return needsDoctor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Patient getPatient() {
        return patient;
    }
}
