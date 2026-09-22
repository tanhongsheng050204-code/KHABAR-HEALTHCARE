package com.khabar.api.readings;

import com.khabar.api.followup.TriageLevel;
import com.khabar.api.patients.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One home reading: blood pressure or blood sugar, typed in or sent by a linked device. */
@Entity
@Table(name = "reading")
public class Reading {

    public enum Kind { BLOOD_PRESSURE, GLUCOSE }

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    private Integer systolic;

    private Integer diastolic;

    /** mmol/L */
    private Double glucose;

    @Column(nullable = false)
    private Instant measuredAt;

    /** patient, caregiver, clinic or favoriot */
    @Column(nullable = false, length = 20)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriageLevel level;

    @Column(nullable = false, length = 100)
    private String description;

    /** Set when the clinic has called about it. */
    private Instant handledAt;

    private UUID handledBy;

    protected Reading() {
    }

    private Reading(Patient patient, Kind kind, Integer systolic, Integer diastolic, Double glucose, Instant measuredAt, String source,
                    ReadingLevels.Assessment assessment) {
        this.id = UUID.randomUUID();
        this.patient = patient;
        this.kind = kind;
        this.systolic = systolic;
        this.diastolic = diastolic;
        this.glucose = glucose;
        this.measuredAt = measuredAt;
        this.source = source;
        this.level = assessment.level();
        this.description = assessment.description();
    }

    public static Reading glucose(Patient patient, double mmol, Instant measuredAt, String source) {
        return new Reading(patient, Kind.GLUCOSE, null, null, mmol, measuredAt, source, ReadingLevels.glucose(mmol));
    }

    public static Reading bloodPressure(Patient patient, int systolic, int diastolic, Instant measuredAt, String source) {
        return new Reading(patient, Kind.BLOOD_PRESSURE, systolic, diastolic, null, measuredAt, source,
                ReadingLevels.bloodPressure(systolic, diastolic));
    }

    public void markHandled(UUID clinicianId, Instant when) {
        this.handledBy = clinicianId;
        this.handledAt = when;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public Kind getKind() {
        return kind;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    public String getSource() {
        return source;
    }

    public TriageLevel getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }
}
