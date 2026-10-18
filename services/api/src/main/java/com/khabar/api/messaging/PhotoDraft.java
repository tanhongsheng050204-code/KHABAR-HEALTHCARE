package com.khabar.api.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A packet photo a patient sent, waiting for their consent before it is read. Only Telegram's file id is kept. */
@Entity
@Table(name = "photo_draft")
public class PhotoDraft {

    @Id
    private UUID patientId;

    @Column(nullable = false, length = 300)
    private String fileId;

    @Column(nullable = false)
    private Instant createdAt;

    protected PhotoDraft() {
    }

    public PhotoDraft(UUID patientId, String fileId, Instant createdAt) {
        this.patientId = patientId;
        this.fileId = fileId;
        this.createdAt = createdAt;
    }

    public UUID getPatientId() { return patientId; }
    public String getFileId() { return fileId; }
    public Instant getCreatedAt() { return createdAt; }
}
