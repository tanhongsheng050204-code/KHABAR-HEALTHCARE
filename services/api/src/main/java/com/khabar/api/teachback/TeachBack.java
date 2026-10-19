package com.khabar.api.teachback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One go at "check you understood your plan": how many of the questions about the doctor's plan the patient got
 * right, and which ones they missed. A prompt for the clinic to explain again, not a test result.
 */
@Entity
@Table(name = "teach_back", indexes = {
        @Index(name = "ix_teach_back_patient", columnList = "patient_id"),
        @Index(name = "ix_teach_back_clinic", columnList = "clinic_id")})
public class TeachBack {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(nullable = false)
    private UUID encounterId;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int correct;

    /** Comma-separated question ids the patient got wrong, e.g. "timing:0,next_visit". */
    @Column(length = 1000)
    private String missed;

    @Column(nullable = false)
    private Instant takenAt;

    protected TeachBack() {
    }

    public TeachBack(UUID patientId, UUID clinicId, UUID encounterId, int total, int correct, String missed, Instant takenAt) {
        this.id = UUID.randomUUID();
        this.patientId = patientId;
        this.clinicId = clinicId;
        this.encounterId = encounterId;
        this.total = total;
        this.correct = correct;
        this.missed = missed;
        this.takenAt = takenAt;
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public UUID getEncounterId() { return encounterId; }
    public int getTotal() { return total; }
    public int getCorrect() { return correct; }
    public String getMissed() { return missed; }
    public Instant getTakenAt() { return takenAt; }
}
