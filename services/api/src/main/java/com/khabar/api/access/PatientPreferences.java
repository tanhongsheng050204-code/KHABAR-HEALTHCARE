package com.khabar.api.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** How a patient prefers to use Khabar: voice first, and the simple large-print screen. Both off by default. */
@Entity
@Table(name = "patient_preferences")
public class PatientPreferences {

    @Id
    private UUID patientId;

    @Column(nullable = false)
    private boolean voiceFirst;

    @Column(nullable = false)
    private boolean simpleMode;

    private Instant updatedAt;

    protected PatientPreferences() {
    }

    public PatientPreferences(UUID patientId) {
        this.patientId = patientId;
    }

    void update(boolean voiceFirst, boolean simpleMode, Instant at) {
        this.voiceFirst = voiceFirst;
        this.simpleMode = simpleMode;
        this.updatedAt = at;
    }

    public UUID getPatientId() { return patientId; }
    public boolean isVoiceFirst() { return voiceFirst; }
    public boolean isSimpleMode() { return simpleMode; }
}
