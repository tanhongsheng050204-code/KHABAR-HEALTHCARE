package com.khabar.api.reminders;

import com.khabar.api.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One dose reminder: which medicines, which time of day, and what the patient answered (Taken, Missed or
 * nothing). One row per patient, day and time of day, so a reminder is never sent twice.
 */
@Entity
@Table(name = "dose_log",
        uniqueConstraints = @UniqueConstraint(name = "ux_dose_log_slot", columnNames = {"patient_id", "dose_day", "slot"}),
        indexes = @Index(name = "ix_dose_log_patient", columnList = "patient_id"))
public class DoseLog {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(nullable = false)
    private UUID encounterId;

    @Column(name = "dose_day", nullable = false)
    private LocalDate day;

    /** morning, afternoon, evening, night, sahur or berbuka. */
    @Column(nullable = false, length = 20)
    private String slot;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "medicines_enc", nullable = false, length = 4096)
    private String medicines;

    @Column(nullable = false)
    private Instant sentAt;

    private boolean delivered;

    /** TAKEN or MISSED, or null while unanswered. */
    @Column(length = 10)
    private String response;

    private Instant respondedAt;

    protected DoseLog() {
    }

    public DoseLog(UUID patientId, UUID encounterId, LocalDate day, String slot, String medicines, Instant sentAt) {
        this.id = UUID.randomUUID();
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.day = day;
        this.slot = slot;
        this.medicines = medicines;
        this.sentAt = sentAt;
    }

    public void delivered(boolean delivered) {
        this.delivered = delivered;
    }

    /** Records the first answer only; a second tap changes nothing. */
    public boolean answer(String response, Instant at) {
        if (this.response != null) {
            return false;
        }
        this.response = response;
        this.respondedAt = at;
        return true;
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public UUID getEncounterId() { return encounterId; }
    public LocalDate getDay() { return day; }
    public String getSlot() { return slot; }
    public String getMedicines() { return medicines; }
    public Instant getSentAt() { return sentAt; }
    public boolean isDelivered() { return delivered; }
    public String getResponse() { return response; }
    public Instant getRespondedAt() { return respondedAt; }
}
