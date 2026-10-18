package com.khabar.api.messaging;

import com.khabar.api.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The transcript of a patient's latest Telegram voice note, waiting for them to confirm it. Nothing reaches the
 * clinic until they tap Send; typing anything else discards it. One per patient.
 */
@Entity
@Table(name = "voice_draft")
public class VoiceDraft {

    @Id
    private UUID patientId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "text_enc", nullable = false, length = 8192)
    private String text;

    @Column(nullable = false)
    private Instant createdAt;

    /** Telegram's message id of the voice note, so a confirmed reply keeps a stable retry key. */
    @Column(length = 100)
    private String sourceMessageId;

    protected VoiceDraft() {
    }

    public VoiceDraft(UUID patientId, String text, String sourceMessageId, Instant createdAt) {
        this.patientId = patientId;
        this.text = text;
        this.sourceMessageId = sourceMessageId;
        this.createdAt = createdAt;
    }

    public UUID getPatientId() { return patientId; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
    public String getSourceMessageId() { return sourceMessageId; }
}
