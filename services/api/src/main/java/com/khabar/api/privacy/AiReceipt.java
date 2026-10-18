package com.khabar.api.privacy;

import com.khabar.api.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One time patient-related text (or a photo or recording) left the clinical API for the AI service: what was
 * sent, after identifiers were removed, and how many were removed. Rows are only ever added.
 */
@Entity
@Table(name = "ai_receipt", indexes = @Index(name = "ix_ai_receipt_patient", columnList = "patient_id"))
public class AiReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /** One of AiReceipts.Purpose, stored as text. */
    @Column(nullable = false, length = 40)
    private String purpose;

    /** The AI-service endpoint it went to. */
    @Column(nullable = false, length = 100)
    private String endpoint;

    /** Exactly what was sent (truncated if very long), or a description for a photo or recording. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "sent_enc", nullable = false, length = 16384)
    private String sent;

    private boolean truncated;

    /** True when what was sent is not text (a photo or a recording), so `sent` describes it instead. */
    private boolean media;

    @Column(nullable = false)
    private int namesRemoved;

    @Column(nullable = false)
    private int icsRemoved;

    @Column(nullable = false)
    private int phonesRemoved;

    @Column(nullable = false)
    private Instant at;

    protected AiReceipt() {
    }

    public AiReceipt(UUID patientId, String purpose, String endpoint, String sent, boolean truncated, boolean media,
                     int names, int ics, int phones, Instant at) {
        this.patientId = patientId;
        this.purpose = purpose;
        this.endpoint = endpoint;
        this.sent = sent;
        this.truncated = truncated;
        this.media = media;
        this.namesRemoved = names;
        this.icsRemoved = ics;
        this.phonesRemoved = phones;
        this.at = at;
    }

    public Long getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public String getPurpose() { return purpose; }
    public String getEndpoint() { return endpoint; }
    public String getSent() { return sent; }
    public boolean isTruncated() { return truncated; }
    public boolean isMedia() { return media; }
    public int getNamesRemoved() { return namesRemoved; }
    public int getIcsRemoved() { return icsRemoved; }
    public int getPhonesRemoved() { return phonesRemoved; }
    public Instant getAt() { return at; }
}
