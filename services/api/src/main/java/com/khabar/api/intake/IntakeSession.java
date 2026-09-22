package com.khabar.api.intake;

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

/**
 * One pre-visit intake chat. The transcript is stored with the patient's identifiers already
 * removed, and both it and the pre-visit report are encrypted.
 */
@Entity
@Table(name = "intake_session")
public class IntakeSession {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Patient patient;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant completedAt;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "transcript_enc", nullable = false, length = 200000)
    private String transcriptJson;

    /** Empty when the intake finished but the report could not be built; the doctor still has the transcript. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "report_enc", length = 200000)
    private String reportJson;

    protected IntakeSession() {
    }

    public IntakeSession(Patient patient, Instant startedAt) {
        this.id = UUID.randomUUID();
        this.patient = patient;
        this.startedAt = startedAt;
        this.updatedAt = startedAt;
        this.transcriptJson = "[]";
    }

    public void update(String transcriptJson, Instant when) {
        this.transcriptJson = transcriptJson;
        this.updatedAt = when;
    }

    public void complete(String reportJson, Instant when) {
        this.reportJson = reportJson;
        this.completedAt = when;
        this.updatedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getTranscriptJson() {
        return transcriptJson;
    }

    public String getReportJson() {
        return reportJson;
    }
}
