package com.khabar.api.pathways;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A patient on a condition pathway (diabetes, hypertension or after an operation), with the targets the doctor set
 * and the date of the next review. Targets start from the pathway's defaults and the doctor can change them.
 */
@Entity
@Table(name = "patient_pathway", indexes = {
        @Index(name = "ix_patient_pathway_patient", columnList = "patient_id"),
        @Index(name = "ix_patient_pathway_clinic", columnList = "clinic_id")})
public class PatientPathway {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    /** DIABETES, HYPERTENSION or POST_OP. */
    @Column(nullable = false, length = 20)
    private String pathway;

    /** Fasting glucose upper target, mmol/L. */
    private Double glucoseMax;

    private Integer systolicMax;

    private Integer diastolicMax;

    /** HbA1c target, %. */
    private Double hba1cMax;

    @Column(nullable = false)
    private int reviewEveryWeeks;

    private LocalDate nextReviewOn;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private UUID startedBy;

    private Instant endedAt;

    protected PatientPathway() {
    }

    public PatientPathway(UUID patientId, UUID clinicId, String pathway, Pathways.Targets targets, int reviewEveryWeeks,
                          LocalDate nextReviewOn, UUID startedBy, Instant startedAt) {
        this.id = UUID.randomUUID();
        this.patientId = patientId;
        this.clinicId = clinicId;
        this.pathway = pathway;
        apply(targets);
        this.reviewEveryWeeks = reviewEveryWeeks;
        this.nextReviewOn = nextReviewOn;
        this.startedBy = startedBy;
        this.startedAt = startedAt;
    }

    void apply(Pathways.Targets targets) {
        this.glucoseMax = targets.glucoseMax();
        this.systolicMax = targets.systolicMax();
        this.diastolicMax = targets.diastolicMax();
        this.hba1cMax = targets.hba1cMax();
    }

    void schedule(int everyWeeks, LocalDate next) {
        this.reviewEveryWeeks = everyWeeks;
        this.nextReviewOn = next;
    }

    void end(Instant at) {
        this.endedAt = at;
    }

    public Pathways.Targets targets() {
        return new Pathways.Targets(glucoseMax, systolicMax, diastolicMax, hba1cMax);
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public UUID getClinicId() { return clinicId; }
    public String getPathway() { return pathway; }
    public int getReviewEveryWeeks() { return reviewEveryWeeks; }
    public LocalDate getNextReviewOn() { return nextReviewOn; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public boolean isActive() { return endedAt == null; }
}
