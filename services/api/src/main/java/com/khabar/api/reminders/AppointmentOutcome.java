package com.khabar.api.reminders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A booked appointment the patient did not come to: no visit was started for them that day. It puts the patient
 * on the call list until someone records contact.
 */
@Entity
@Table(name = "appointment_outcome", indexes = @Index(name = "ix_appointment_outcome_clinic", columnList = "clinic_id"))
public class AppointmentOutcome {

    @Id
    private UUID appointmentId;

    @Column(nullable = false)
    private UUID patientId;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    /** NO_SHOW for now. */
    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(nullable = false)
    private Instant appointmentAt;

    @Column(nullable = false)
    private Instant detectedAt;

    private Instant handledAt;

    private UUID handledBy;

    protected AppointmentOutcome() {
    }

    public AppointmentOutcome(UUID appointmentId, UUID patientId, UUID clinicId, Instant appointmentAt, Instant detectedAt) {
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.clinicId = clinicId;
        this.outcome = "NO_SHOW";
        this.appointmentAt = appointmentAt;
        this.detectedAt = detectedAt;
    }

    public void handled(UUID by, Instant at) {
        this.handledBy = by;
        this.handledAt = at;
    }

    public UUID getAppointmentId() { return appointmentId; }
    public UUID getPatientId() { return patientId; }
    public UUID getClinicId() { return clinicId; }
    public String getOutcome() { return outcome; }
    public Instant getAppointmentAt() { return appointmentAt; }
    public Instant getDetectedAt() { return detectedAt; }
    public Instant getHandledAt() { return handledAt; }
}
