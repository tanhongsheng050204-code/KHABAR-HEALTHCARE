package com.khabar.api.labs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One laboratory result the doctor entered: HbA1c, LDL cholesterol, eGFR or creatinine. */
@Entity
@Table(name = "lab_result", indexes = @Index(name = "ix_lab_result_patient", columnList = "patient_id"))
public class LabResult {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /** HBA1C, LDL, EGFR or CREATININE. */
    @Column(name = "test_code", nullable = false, length = 20)
    private String test;

    @Column(name = "result_value", nullable = false)
    private double value;

    @Column(nullable = false)
    private LocalDate takenOn;

    @Column(nullable = false)
    private UUID recordedBy;

    @Column(nullable = false)
    private Instant recordedAt;

    protected LabResult() {
    }

    public LabResult(UUID patientId, String test, double value, LocalDate takenOn, UUID recordedBy, Instant recordedAt) {
        this.id = UUID.randomUUID();
        this.patientId = patientId;
        this.test = test;
        this.value = value;
        this.takenOn = takenOn;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public String getTest() { return test; }
    public double getValue() { return value; }
    public LocalDate getTakenOn() { return takenOn; }
    public Instant getRecordedAt() { return recordedAt; }
}
